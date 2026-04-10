# TeleHop

Cross-server teleportation plugin for **Paper + Velocity** networks.

## Features

- **Network Spawn** `/spawn` sends players to the hub server, cross-server
- **Admin Warps** `/warp`, `/setwarp`, `/delwarp`, `/warps` — shared across all servers via MySQL
- **Player Warps** `/pwarp` — personal warps with per-rank limits, public/private toggle, cross-server
- **TPA** `/tpa`, `/tpahere`, `/tpaaccept`, `/tpadeny`, `/tpacancel` — works across servers
- **Random Teleport** `/rtp` — GUI region + dimension picker, safe landing, configurable radius
- **Admin Teleport** `/tp`, `/tphere` — cross-server admin TP
- **Feature Toggles** enable/disable any module per server without removing commands
- **Multi-Language** 6 built-in languages (en, nl, de, es, zh, pl) with automatic fallback
- **Admin Tools** `/telehop reload/version/perms/help`, `/listwarps`, `/forcedelwarp`
- **Tab Completion** player and warp names autocomplete across the entire network

## Requirements

| Software | Version |
|----------|---------|
| Paper | 1.21+ |
| Velocity | 3.3+ |
| Java | 21+ |
| MySQL | 8.0+ (MariaDB 10.5+ also works) |
| LuckPerms | 5.4+ (optional, recommended) |

## Quick Start

1. Create a MySQL database and user
2. Place `telehop-velocity-1.0.0.jar` on your Velocity proxy
3. Place `telehop-paper-1.0.0.jar` on each Paper backend
4. Set MySQL credentials and `server-name` on each server
5. Restart Velocity first, then all Paper servers

See [docs/setup.md](docs/setup.md) for the full walkthrough.

## Documentation

| Guide | Description |
|-------|-------------|
| [Setup](docs/setup.md) | Installation, MySQL, Velocity + Paper configuration, language setup |
| [Commands](docs/commands.md) | Every command with syntax, description, and required permission |
| [Permissions](docs/permissions.md) | All permission nodes, defaults, and LuckPerms examples |
| [Configuration](docs/configuration.md) | Full Paper `config.yml` and Velocity `config.properties` reference |
| [Warps](docs/warps.md) | Admin warps vs player warps, limits, public/private, cross-server |
| [Messages](docs/messages.md) | Language system, all message keys, MiniMessage colors, placeholders |
| [Troubleshooting](docs/troubleshooting.md) | Common issues and fixes |

## Database

Tables are created automatically on first startup. For manual setup or reference, see [`sql/schema.sql`](sql/schema.sql).

| Table | Purpose |
|-------|---------|
| `players` | Tracks which server each player is on |
| `warps` | Admin warps (name, location, server) |
| `player_warps` | Player warps (owner, name, location, public/private) |
| `tpa_requests` | Active TPA requests with expiry |

## Project Structure

```
telehop-plugin/
├── common/          Shared code (DB, models, services)
├── paper/           Paper backend plugin
├── velocity/        Velocity proxy plugin
├── docs/            Documentation
└── sql/             Database schema reference
```

## Building

```bash
mvn clean package
```

Produces:
- `paper/target/telehop-paper-1.0.0.jar`
- `velocity/target/telehop-velocity-1.0.0.jar`

Requires Java 21+ and Maven 3.8+.

## Author

**Epildev** — [GitHub](https://github.com/GitEpildev) · [Website](https://developer.epildevconnect.uk/myhub/home) · Discord: `Epildev`

Developed by [Epildevconnect Ltd](https://developer.epildevconnect.uk/myhub/home).

## License

MIT License — free to use, modify, and distribute. See [LICENSE](LICENSE) for details.
