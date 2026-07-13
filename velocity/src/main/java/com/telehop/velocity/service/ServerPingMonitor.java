package com.telehop.velocity.service;

import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import com.telehop.velocity.messaging.VelocityMessagingManager;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Measures the proxy-to-backend round-trip time for every configured backend
 * server and broadcasts the results to all backends so Paper-side GUIs can
 * show players the ping they will get on a destination server.
 *
 * <p>Measurement uses a timed {@link RegisteredServer#ping()} (a status ping)
 * every {@value #INTERVAL_SECONDS} seconds. Results are broadcast as a
 * {@link PacketType#SERVER_PING_UPDATE} packet with a payload of
 * {@code server=rttMs} pairs.</p>
 */
public final class ServerPingMonitor {

    private static final long INTERVAL_SECONDS = 10;
    private static final long PING_TIMEOUT_MS = 5000;

    private final Object plugin;
    private final ProxyServer proxy;
    private final VelocityMessagingManager messaging;
    private final List<String> backends;
    private final Logger logger;

    private final Map<String, Long> rtts = new ConcurrentHashMap<>();
    private ScheduledTask task;

    public ServerPingMonitor(Object plugin, ProxyServer proxy, VelocityMessagingManager messaging,
                             List<String> backends, Logger logger) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.messaging = messaging;
        this.backends = backends;
        this.logger = logger;
    }

    public void start() {
        task = proxy.getScheduler()
                .buildTask(plugin, this::measureAndBroadcast)
                .delay(5, TimeUnit.SECONDS)
                .repeat(INTERVAL_SECONDS, TimeUnit.SECONDS)
                .schedule();
        logger.info("ServerPingMonitor started ({} backends, every {}s)", backends.size(), INTERVAL_SECONDS);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void measureAndBroadcast() {
        for (String name : backends) {
            proxy.getServer(name).ifPresent(this::measure);
        }
        // Broadcast whatever we have so far; the very first cycle may be
        // one interval behind, which is fine for a GUI hint.
        if (!rtts.isEmpty()) {
            broadcast();
        }
    }

    private void measure(RegisteredServer server) {
        String name = server.getServerInfo().getName();
        long start = System.nanoTime();
        server.ping()
                .orTimeout(PING_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .whenComplete((ping, error) -> {
                    if (error != null) {
                        rtts.remove(name);
                        return;
                    }
                    long rttMs = (System.nanoTime() - start) / 1_000_000L;
                    rtts.put(name, rttMs);
                });
    }

    private void broadcast() {
        StringJoiner joiner = new StringJoiner(",");
        rtts.forEach((server, rtt) -> joiner.add(server + "=" + rtt));
        String encoded = joiner.toString();

        for (String backend : backends) {
            NetworkPacket packet = NetworkPacket.request(PacketType.SERVER_PING_UPDATE, "velocity", backend)
                    .put("rtts", encoded)
                    .put("measuredAt", String.valueOf(System.currentTimeMillis()));
            messaging.sendToServer(backend, packet);
        }
    }
}
