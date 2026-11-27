# Redis for multi-proxy networks

Velocity uses Redis when the selected mode is `MULTI`, or when a `CUSTOM` profile enables `proxy.multi-proxy-enabled`. One proxy running `SINGLE` does not connect to Redis. Backends communicate with their trusted Velocity proxy on the TeleHop plugin channel.

Edit `plugins/telehop-velocity/config/redis.yml` on each proxy. The file is shared by all three profiles on that instance. This example shows TLS on a private Redis service. Choose the address, port and authentication details for your service.

```yaml
redis:
  host: "redis.internal"
  port: 6380
  password: "replace-with-a-private-password"
  channel-prefix: "telehop"
  ssl: true
  ssl-verify: true
  ssl-ca-cert: ""
```

Use the same channel prefix on all proxies serving the same TeleHop network. Store authentication details in this file and restrict access to the plugin folder and migration backups. The new-install defaults target `127.0.0.1:6379` with TLS disabled and an empty password. With TLS enabled, `ssl-verify: true` verifies the certificate and hostname. Leave `ssl-ca-cert` empty to use the JVM trust store or set it to the PEM CA certificate path readable by the proxy process.

In `config/multi/proxy.yml` or `config/custom/proxy.yml`, assign a distinct `proxy.id` to each proxy, check `proxy.global-player-list` and configure `proxy.cross-proxy-timeout-ms`. Restart each proxy after edits. Read its startup and subscriber logs, then test player lookup and teleport requests between players on different proxies. Check the host, port, CA, hostname and authentication if the subscriber cannot connect. [Multi-proxy configuration](multi-proxy.md) · [Velocity reference](velocity.md)
