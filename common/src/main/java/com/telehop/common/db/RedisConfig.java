package com.telehop.common.db;

/**
 * Immutable configuration for a Redis connection used by multi-proxy support.
 * When {@code enabled} is false, no Redis connection is established.
 *
 * @param enabled       whether Redis-based multi-proxy communication is active
 * @param host          Redis hostname or IP
 * @param port          Redis port (1-65535)
 * @param password      Redis password (empty string for no auth)
 * @param channelPrefix prefix for all Redis pub/sub channels (e.g. "telehop")
 * @param proxyId       unique identifier for this proxy instance (e.g. "proxy-usa-1")
 * @param ssl           whether to connect over TLS (rediss)
 * @param sslVerify     whether to verify the server certificate and hostname
 * @param sslCaCert     optional path to a PEM CA certificate for self-signed
 *                      setups (empty string uses the system trust store)
 */
public record RedisConfig(
        boolean enabled,
        String host,
        int port,
        String password,
        String channelPrefix,
        String proxyId,
        boolean ssl,
        boolean sslVerify,
        String sslCaCert
) {
    public RedisConfig {
        if (host == null || host.isBlank())
            throw new IllegalArgumentException("Redis host must not be blank");
        if (port < 1 || port > 65535)
            throw new IllegalArgumentException("Redis port must be 1-65535, got " + port);
        if (channelPrefix == null || channelPrefix.isBlank())
            throw new IllegalArgumentException("Redis channel prefix must not be blank");
        if (proxyId == null || proxyId.isBlank())
            throw new IllegalArgumentException("Proxy ID must not be blank");
        if (sslCaCert == null)
            sslCaCert = "";
    }

    public String crossProxyChannel() {
        return channelPrefix + ":cross-proxy";
    }
}
