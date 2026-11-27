# Velocity configuration

Install the proxy JAR on each Velocity proxy. Register the actual backend addresses in Velocity's own `velocity.toml`. TeleHop checks its configured hub and backend list against those registrations during startup. On a fresh proxy the first configured connection attempt supplies the initial hub. Configure the preferred hub explicitly before opening a multi-server network.

All paths below are relative to the Velocity plugin data folder, usually `plugins/telehop-velocity/`. In profile paths substitute `single`, `multi` or `custom` for `<profile>`. Each proxy reads settings at startup.

| File | Settings |
| --- | --- |
| `config.yml` | Profile and migration settings |
| `config/<profile>/servers.yml` | `servers.hub` and the YAML list `servers.backends` |
| `config/multi/proxy.yml`, `config/custom/proxy.yml` (SINGLE uses `config/single/servers.yml`) | Unique `proxy.id`, `proxy.global-player-list`, `proxy.cross-proxy-timeout-ms` and `proxy.multi-proxy-enabled` for CUSTOM |
| `config/<profile>/messaging.yml` | `messaging.request-timeout-ms` and `messaging.dedupe-window-ms` |
| `config/database.yml` | `mysql.host`, `port`, `database`, `username`, `password`, `pool-size` |
| `config/redis.yml` | `redis.host`, `port`, `password`, `channel-prefix`, `ssl`, `ssl-verify`, `ssl-ca-cert` |

For one proxy choose `SINGLE`. For multiple proxies choose `MULTI` and configure the same database, Redis service and channel prefix on each instance. Assign distinct proxy IDs. Keep `ssl-verify: true` for an authenticated TLS connection. Set `ssl-ca-cert` to the local PEM certificate path when using a private CA. Redis credentials and private database settings belong in the shared files. Keep backups private.

In `CUSTOM`, `proxy.multi-proxy-enabled` decides whether to connect to Redis. `SINGLE` uses the local proxy and `MULTI` connects to Redis. A profile switch takes effect on the next start. TeleHop preserves the original authentication and TLS choices when migrating from properties. [Upgrade and recovery](migration.md) · [Network routing](multi-proxy.md)
