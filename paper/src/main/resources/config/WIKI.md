# TeleHop configuration

Open `plugins/TeleHop/config.yml` to select a deployment profile. Use `SINGLE` for standalone or one-proxy networks, `MULTI` for shared multi-proxy networks, and `CUSTOM` for manual topology control. The matching lowercase directory under `config/` owns the backend's operational settings.

| File or directory | Purpose |
| --- | --- |
| `config/<profile>/general.yml` | Backend name registered on Velocity, global hub, language and audit |
| `config/<profile>/spawns.yml` | Destinations and per-world or backend routes |
| `config/<profile>/features.yml` | Enable features and the optional hub command |
| `messaging.yml`, `regions.yml` in `config/multi/` and `config/custom/` | Proxy transport, timeouts and regional hub membership. SINGLE keeps both sections in `config/single/general.yml`. |
| `config/<profile>/rtp.yml`, `respawn.yml` | RTP options, regions, worlds and death respawn |
| `config/<profile>/home.yml`, `teleport.yml`, `tpa.yml` | Home menus, effects, cooldowns and requests |
| `config/database.yml` | Shared MySQL or MariaDB credentials |
| `commands/`, `languages/` | Command groups and messages |
| `storage.yml` | Default spawn position saved in game |
| `migrations/` | Upgrade history, checksums and private backups |

Set `features.hub: true` in the active feature file and keep `features.spawn` enabled. To rename the command, enable custom names in the root and edit `commands/hub.yml`. Restart the backend after changing command labels or registration. Run `/telehop config validate` and `/telehop reload` for reloadable settings.

Configure Velocity separately in its plugin data folder. Each proxy must share the correct database, and in MULTI mode the Redis connection and channel prefix. Give each proxy its own identifier. Check the configured backend names against Velocity's registered servers.

Use `/telehop spawn setdefault` in game to save the backend spawn. Add named spawn locations under `destinations` in the active spawn file and grant `telehop.spawn.destination.<name>` for restricted access. [Spawn setup](https://github.com/gitEpildev/TeleHop/blob/main/docs/spawns.md) · [Permissions](https://github.com/gitEpildev/TeleHop/blob/main/docs/permissions.md)

Make a consistent network database backup and retain each instance's plugin folder before upgrading. [Configuration reference](https://github.com/gitEpildev/TeleHop/blob/main/docs/configuration.md) · [Migration and recovery](https://github.com/gitEpildev/TeleHop/blob/main/docs/migration.md)
