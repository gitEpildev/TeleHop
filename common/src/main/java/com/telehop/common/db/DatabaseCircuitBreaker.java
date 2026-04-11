package com.telehop.common.db;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Lightweight circuit breaker that sits in front of {@link DatabaseManager}
 * async operations. When consecutive database failures exceed a threshold the
 * breaker <em>opens</em> and fails fast, giving MySQL time to recover without
 * saturating the connection pool or blocking the main thread.
 *
 * <p>State machine: {@code CLOSED → OPEN → HALF_OPEN → CLOSED}.</p>
 *
 * <ul>
 *   <li><b>CLOSED</b> — normal operation; every failure increments the counter.</li>
 *   <li><b>OPEN</b> — all calls fail immediately with {@link CircuitOpenException}.
 *       After {@code recoveryMs} elapses the breaker moves to HALF_OPEN.</li>
 *   <li><b>HALF_OPEN</b> — a single probe call is allowed through. Success resets
 *       to CLOSED; failure re-opens the circuit.</li>
 * </ul>
 */
public final class DatabaseCircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final long recoveryMs;
    private final Logger logger;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong openedAt = new AtomicLong(0);
    private volatile State state = State.CLOSED;

    /**
     * @param failureThreshold consecutive failures before the circuit opens
     * @param recoveryMs       milliseconds to wait in OPEN before allowing a probe
     * @param logger           logger for state transitions (nullable)
     */
    public DatabaseCircuitBreaker(int failureThreshold, long recoveryMs, Logger logger) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.recoveryMs = Math.max(1_000, recoveryMs);
        this.logger = logger;
    }

    public DatabaseCircuitBreaker(int failureThreshold, long recoveryMs) {
        this(failureThreshold, recoveryMs, null);
    }

    public State state() {
        if (state == State.OPEN && System.currentTimeMillis() - openedAt.get() >= recoveryMs) {
            state = State.HALF_OPEN;
            log("Circuit breaker HALF_OPEN — allowing probe request");
        }
        return state;
    }

    /**
     * Wraps an async database operation with circuit breaker protection.
     * If the circuit is open the returned future fails immediately.
     */
    public <T> CompletableFuture<T> execute(Supplier<CompletableFuture<T>> operation) {
        State current = state();
        if (current == State.OPEN) {
            return CompletableFuture.failedFuture(
                    new CircuitOpenException("Database circuit breaker is OPEN — failing fast"));
        }

        return operation.get().whenComplete((result, ex) -> {
            if (ex != null) {
                onFailure();
            } else {
                onSuccess();
            }
        });
    }

    /**
     * Wraps a {@code Runnable}-style async operation (returns {@code Void}).
     */
    public CompletableFuture<Void> executeRun(Supplier<CompletableFuture<Void>> operation) {
        return execute(operation);
    }

    private void onSuccess() {
        if (state != State.CLOSED) {
            log("Circuit breaker CLOSED — database recovered");
        }
        consecutiveFailures.set(0);
        state = State.CLOSED;
    }

    private void onFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= failureThreshold && state != State.OPEN) {
            state = State.OPEN;
            openedAt.set(System.currentTimeMillis());
            log("Circuit breaker OPEN — " + failures + " consecutive failures (threshold: " + failureThreshold + ")");
        }
    }

    public int consecutiveFailures() {
        return consecutiveFailures.get();
    }

    public void reset() {
        consecutiveFailures.set(0);
        state = State.CLOSED;
    }

    private void log(String message) {
        if (logger != null) {
            logger.warning("[TeleHop-DB] " + message);
        }
    }

    /**
     * Thrown when the circuit breaker is open and rejecting requests.
     */
    public static final class CircuitOpenException extends RuntimeException {
        public CircuitOpenException(String message) {
            super(message);
        }
    }
}
