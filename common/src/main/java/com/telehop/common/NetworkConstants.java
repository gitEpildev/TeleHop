package com.telehop.common;

/**
 * Shared constants for the plugin messaging protocol between Paper and Velocity.
 */
public final class NetworkConstants {
    public static final String CHANNEL_ID = "telehop:network";
    public static final long DEFAULT_REQUEST_TIMEOUT_MS = 10_000L;

    public static final String SPAWN_CAPABILITY = "named-spawns-v2";

    private NetworkConstants() {
    }
}
