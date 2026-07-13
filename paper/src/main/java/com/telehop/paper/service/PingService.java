package com.telehop.paper.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches the proxy-to-backend round-trip times broadcast by the Velocity
 * ServerPingMonitor. Used by the RTP region GUI to estimate the ping a
 * player would get on a destination server.
 */
public final class PingService {

    private final Map<String, Long> proxyRtts = new ConcurrentHashMap<>();
    private volatile long measuredAt;

    /** Replaces the cached RTT map with a fresh broadcast from the proxy. */
    public void update(Map<String, Long> rtts, long measuredAt) {
        proxyRtts.clear();
        proxyRtts.putAll(rtts);
        this.measuredAt = measuredAt;
    }

    /** Proxy-to-server RTT in ms, or -1 if unknown. */
    public long proxyRtt(String serverName) {
        Long rtt = proxyRtts.get(serverName);
        return rtt != null ? rtt : -1L;
    }

    /** Milliseconds since the proxy last broadcast measurements, or -1 if never. */
    public long ageMs() {
        return measuredAt > 0 ? System.currentTimeMillis() - measuredAt : -1L;
    }

    public boolean hasData() {
        return !proxyRtts.isEmpty();
    }

    /**
     * Estimates the ping a player would experience on {@code targetServer}.
     * The player's measured ping covers client to proxy to current server;
     * swapping the current-server leg for the target-server leg gives a
     * close approximation of the post-transfer ping.
     *
     * @param playerPing    the player's current ping in ms
     * @param currentServer the Velocity name of the server the player is on
     * @param targetServer  the Velocity name of the destination server
     * @return estimated ping in ms, never negative; falls back to
     *         {@code playerPing} when RTT data is missing
     */
    public long estimateDestinationPing(long playerPing, String currentServer, String targetServer) {
        long currentLeg = proxyRtt(currentServer);
        long targetLeg = proxyRtt(targetServer);
        if (currentLeg < 0 || targetLeg < 0) {
            return Math.max(0, playerPing);
        }
        return Math.max(0, playerPing - currentLeg + targetLeg);
    }
}
