# Shared database

All backend and proxy instances in a TeleHop network use one MySQL 8 or MariaDB database. Each instance reads its own `config/database.yml` relative to the plugin data folder. Changing the deployment profile preserves that file. A standalone backend also needs the database for its persistent features.

```yaml
mysql:
  host: "127.0.0.1"
  port: 3306
  database: "telehop"
  username: "telehop"
  password: "replace-with-a-private-password"
  pool-size: 5
```

Use the database address reachable from the particular Minecraft process. In separate containers, `127.0.0.1` refers to each container itself. The account must be allowed to connect from that host and create and update TeleHop's schema. The pool size is per instance. Restart after changing connection settings. The supplied JDBC configuration uses a connection without TLS. Keep the connection on a protected network or provide a private encrypted tunnel between hosts. [Account creation](setup.md#step-1---create-the-mysql-database)

| Tables | Stored information |
| --- | --- |
| `players`, `last_locations` | Player identity, backend, last-seen time and saved positions |
| `homes`, `warps`, `player_warps` | Home and warp names, ownership, access, world and coordinates |
| `tpa_requests` | Request participants, type and timestamp |
| `spawn_backends`, `spawn_routes`, `named_spawns` | Each backend's published spawn settings, overrides and named destinations, including access and revision |

Home creation uses a per-player database lock so simultaneous saves on different backends respect the same allowance. Empty menu slots consume no database rows. Changing a rank leaves the stored locations intact.

At startup a database lock serialises schema upgrades across instances. Older numerical home slots migrate to named homes while preserving stored locations. Back up the entire database and every instance's TeleHop folder before upgrading. Migration snapshots include configuration; the SQL backup and runtime storage must be captured separately. [Backup and recovery](migration.md) · [Named spawn synchronisation](spawns.md)
