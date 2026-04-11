package com.telehop.common.db;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseCircuitBreakerTest {

    private DatabaseCircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        breaker = new DatabaseCircuitBreaker(3, 500);
    }

    @Test
    void startsInClosedState() {
        assertEquals(DatabaseCircuitBreaker.State.CLOSED, breaker.state());
        assertEquals(0, breaker.consecutiveFailures());
    }

    @Test
    void successfulCallsKeepCircuitClosed() throws Exception {
        CompletableFuture<String> result = breaker.execute(
                () -> CompletableFuture.completedFuture("ok"));
        assertEquals("ok", result.get());
        assertEquals(DatabaseCircuitBreaker.State.CLOSED, breaker.state());
    }

    @Test
    void failuresBelowThresholdKeepCircuitClosed() {
        for (int i = 0; i < 2; i++) {
            breaker.execute(() -> CompletableFuture.failedFuture(new RuntimeException("db down")));
        }
        assertEquals(2, breaker.consecutiveFailures());
        assertEquals(DatabaseCircuitBreaker.State.CLOSED, breaker.state());
    }

    @Test
    void failuresAtThresholdOpenCircuit() {
        for (int i = 0; i < 3; i++) {
            breaker.execute(() -> CompletableFuture.failedFuture(new RuntimeException("db down")));
        }
        assertEquals(DatabaseCircuitBreaker.State.OPEN, breaker.state());
    }

    @Test
    void openCircuitRejectsImmediately() {
        for (int i = 0; i < 3; i++) {
            breaker.execute(() -> CompletableFuture.failedFuture(new RuntimeException("fail")));
        }

        CompletableFuture<String> result = breaker.execute(
                () -> CompletableFuture.completedFuture("should not reach"));

        assertTrue(result.isCompletedExceptionally());
        assertThrows(ExecutionException.class, result::get);
    }

    @Test
    void circuitTransitionsToHalfOpenAfterRecoveryWindow() throws InterruptedException {
        DatabaseCircuitBreaker timedBreaker = new DatabaseCircuitBreaker(3, 1_000);
        for (int i = 0; i < 3; i++) {
            timedBreaker.execute(() -> CompletableFuture.failedFuture(new RuntimeException("fail")));
        }
        assertEquals(DatabaseCircuitBreaker.State.OPEN, timedBreaker.state());

        Thread.sleep(1_200);
        assertEquals(DatabaseCircuitBreaker.State.HALF_OPEN, timedBreaker.state());
    }

    @Test
    void halfOpenSuccessClosesCircuit() throws Exception {
        DatabaseCircuitBreaker timedBreaker = new DatabaseCircuitBreaker(3, 1_000);
        for (int i = 0; i < 3; i++) {
            timedBreaker.execute(() -> CompletableFuture.failedFuture(new RuntimeException("fail")));
        }
        Thread.sleep(1_200);

        CompletableFuture<String> probe = timedBreaker.execute(
                () -> CompletableFuture.completedFuture("recovered"));
        assertEquals("recovered", probe.get());
        assertEquals(DatabaseCircuitBreaker.State.CLOSED, timedBreaker.state());
        assertEquals(0, timedBreaker.consecutiveFailures());
    }

    @Test
    void halfOpenFailureReopensCircuit() throws InterruptedException {
        DatabaseCircuitBreaker timedBreaker = new DatabaseCircuitBreaker(3, 1_000);
        for (int i = 0; i < 3; i++) {
            timedBreaker.execute(() -> CompletableFuture.failedFuture(new RuntimeException("fail")));
        }
        Thread.sleep(1_200);

        timedBreaker.execute(() -> CompletableFuture.failedFuture(new RuntimeException("still down")));
        assertEquals(DatabaseCircuitBreaker.State.OPEN, timedBreaker.state());
    }

    @Test
    void successAfterFailuresResetsCounter() throws Exception {
        breaker.execute(() -> CompletableFuture.failedFuture(new RuntimeException("fail")));
        breaker.execute(() -> CompletableFuture.failedFuture(new RuntimeException("fail")));
        assertEquals(2, breaker.consecutiveFailures());

        breaker.execute(() -> CompletableFuture.completedFuture("ok"));
        assertEquals(0, breaker.consecutiveFailures());
        assertEquals(DatabaseCircuitBreaker.State.CLOSED, breaker.state());
    }

    @Test
    void resetManuallyClosesCircuit() {
        for (int i = 0; i < 3; i++) {
            breaker.execute(() -> CompletableFuture.failedFuture(new RuntimeException("fail")));
        }
        assertEquals(DatabaseCircuitBreaker.State.OPEN, breaker.state());

        breaker.reset();
        assertEquals(DatabaseCircuitBreaker.State.CLOSED, breaker.state());
        assertEquals(0, breaker.consecutiveFailures());
    }
}
