# TeleHop

Cross-server teleportation plugin for **Paper + Velocity** networks.

## Features

- **Network Spawn** `/spawn` sends players to the hub server, cross-server
- **Admin Warps** `/warp`, `/setwarp`, `/delwarp`, `/warps` — shared across all servers via MySQL
- **Player Warps** `/pwarp` — personal warps with per-rank limits, public/private toggle, cross-server
- **TPA** `/tpa`, `/tpahere`, `/tpaaccept`, `/tpadeny`, `/tpacancel` — works across servers
- **TPA Toggle** `/tpatoggle` — session-only toggle to block incoming TPA requests (cross-server aware)
- **Random Teleport** `/rtp` — GUI region + dimension picker, safe landing, configurable radius
- **Homes** `/home`, `/sethome` — GUI with configurable bed colours, permission-based slots (1-5), blocked servers, cross-server
- **Back** `/back`, `/back death` — return to last teleport or death location, cross-server
- **Admin Teleport** `/tp`, `/tphere` — cross-server admin TP
- **Teleport Effects** configurable particles and sounds per teleport type (spawn, tpa, rtp, warp, home, back)
- **Feature Toggles** enable/disable any module per server without removing commands
- **Multi-Language** 6 built-in languages (en, nl, de, es, zh, pl) with automatic fallback
- **Admin Tools** `/telehop reload/version/perms/help`, `/listwarps`, `/forcedelwarp`, `/forcedelhome`
- **Tab Completion** player and warp names autocomplete across the entire network
- **Modular Configuration** split config files (`config/`) with auto-migration from legacy `config.yml`

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
2. Place `telehop-velocity-1.0.1.jar` on your Velocity proxy
3. Place `telehop-paper-1.0.1.jar` on each Paper backend
4. Edit `plugins/TeleHop/config/database.yml` with your MySQL credentials
5. Edit `plugins/TeleHop/config/general.yml` with `server-name` on each server
6. Restart Velocity first, then all Paper servers

See [docs/setup.md](docs/setup.md) for the full walkthrough.

## Documentation

| Guide | Description |
|-------|-------------|
| [Setup](docs/setup.md) | Installation, MySQL, Velocity + Paper configuration, language setup |
| [Commands](docs/commands.md) | Every command with syntax, description, and required permission |
| [Permissions](docs/permissions.md) | All permission nodes, defaults, and LuckPerms examples |
| [Configuration](docs/configuration.md) | Modular Paper `config/` layout and Velocity `config.properties` reference |
| [Protocol](docs/protocol.md) | Plugin messaging protocol — packet types, payload fields, routing, deduplication |
| [Warps](docs/warps.md) | Admin warps vs player warps, limits, public/private, cross-server |
| [Homes](docs/homes.md) | Homes GUI, permission-based slots, blocked servers, world/server colours, cross-server |
| [Messages](docs/messages.md) | Language system, all message keys, MiniMessage colors, placeholders |
| [Troubleshooting](docs/troubleshooting.md) | Common issues and fixes |

## Database

Tables are created automatically on first startup. For manual setup or reference, see [`sql/schema.sql`](sql/schema.sql).

| Table | Purpose |
|-------|---------|
| `players` | Tracks which server each player is on |
| `warps` | Admin warps (name, location, server) |
| `player_warps` | Player warps (owner, name, location, public/private) |
| `tpa_requests` | Active TPA requests with sent_at timestamp |
| `homes` | Player homes (uuid, slot, server, world, coordinates) |

## Paper Configuration Layout

```
plugins/TeleHop/
  config/
    general.yml       # server-name, hub-server, servers list, language, messaging, audit
    database.yml      # MySQL host/port/database/username/password/pool-size
    features.yml      # feature toggles (spawn, rtp, tpa, warps, homes, back, tpa-toggle...)
    teleport.yml      # show-countdown, particles & sounds per teleport type
    tpa.yml           # timeout, cooldown, delay, cancel-on-move
    rtp.yml           # cooldown, delay, max-radius, regions, dimensions, GUI
    home.yml          # max-slots, confirm-set, GUI, beds, blocked-servers, world/server colours
  storage.yml         # runtime-mutable spawn location
  languages/          # en.yml, nl.yml, de.yml, es.yml, zh.yml, pl.yml
```

First-boot auto-migration: if `config.yml` exists but `config/general.yml` does not, the plugin splits the monolithic file into the new layout and renames `config.yml` to `config.yml.old`.

## Project Structure

```
telehop-plugin/
├── common/                Shared code (DB, models, services)
├── paper/                 Paper backend plugin
│   └── src/.../paper/
│       ├── Bootstrap.java         Startup wiring + config migration
│       ├── NetworkPaperPlugin.java Thin lifecycle entry point
│       ├── config/
│       │   ├── PaperSettings.java  Immutable config record (loads from split YAMLs)
│       │   ├── ConfigMigrator.java One-time config.yml → config/ migration
│       │   └── StorageManager.java Runtime-mutable values (storage.yml)
│       ├── handler/
│       │   └── PacketHandler.java  Cross-server packet dispatch
│       ├── command/
│       │   ├── tpa/               TPA commands + TpaToggleCommand
│       │   ├── warp/              Warp + player-warp commands
│       │   ├── home/              HomeCommand, SetHomeCommand
│       │   ├── admin/             Admin commands (/telehop, /tp, /tphere, /forcedelhome)
│       │   ├── SpawnCommand.java
│       │   ├── RtpCommand.java
│       │   └── BackCommand.java
│       ├── service/
│       │   ├── ServiceRegistry.java      Central service holder
│       │   ├── TeleportService.java      Spawn, warp, RTP, home, back + effects
│       │   ├── TeleportEffectPlayer.java Particles & sounds per teleport type
│       │   ├── BackLocationManager.java  In-memory /back locations (session-only)
│       │   ├── TpaRuntimeManager.java    TPA requests, cooldowns, toggle state
│       │   ├── MessageService.java       Language keys + MiniMessage deserialisation
│       │   └── ...
│       └── gui/
│           ├── HomeGui.java       Homes chest GUI (configurable beds, sub-menus)
│           └── RtpGui.java        RTP region/dimension picker
├── velocity/              Velocity proxy plugin
│   └── src/.../velocity/
│       ├── VelocityBootstrap.java            Startup wiring
│       ├── NetworkVelocityPlugin.java        Thin lifecycle entry point
│       ├── config/
│       │   └── VelocitySettings.java         Immutable config record
│       ├── handler/
│       │   └── VelocityPacketHandler.java    Cross-server packet dispatch + routing
│       ├── service/
│       │   ├── VelocityServiceRegistry.java  Central service holder
│       │   ├── VelocityPlayerTracker.java    Player-to-server mapping
│       │   └── PendingActionManager.java     Queued post-transfer actions
│       ├── messaging/
│       │   └── VelocityMessagingManager.java Plugin-message channel
│       └── model/
│           └── PendingAction.java
├── docs/                  Documentation
└── sql/                   Database schema reference
```

## Building

```bash
mvn clean package
```

Produces:
- `paper/target/telehop-paper-1.0.1.jar`
- `velocity/target/telehop-velocity-1.0.1.jar`

Requires Java 21+ and Maven 3.8+.

## CI

GitHub Actions runs `mvn clean verify` on every push and PR to `main`. This includes:
- Compilation (Java 21)
- Unit tests (JUnit 5)
- Checkstyle code quality checks

Build artifacts (Paper + Velocity JARs) are uploaded on successful pushes. See [`.github/workflows/build.yml`](.github/workflows/build.yml).

## Testing

Unit tests live in `common/src/test/` and cover:
- `DatabaseConfigTest` — input validation, JDBC URL generation
- `DatabaseCircuitBreakerTest` — state transitions (CLOSED → OPEN → HALF_OPEN), failure counting, recovery
- `PacketCodecTest` — JSON encode/decode round-trips, payload preservation
- `RequestTrackerTest` — future tracking, timeout, deduplication
- `HomeServiceTest` — async delegation to repository layer (mocked)

Run with:

```bash
mvn test
```

## Author

**Epildev** — [GitHub](https://github.com/GitEpildev) · [Website](https://developer.epildevconnect.uk/myhub/home) · Discord: `Epildev`

Developed by [Epildevconnect Ltd](https://developer.epildevconnect.uk/myhub/home).

## License

MIT License — free to use, modify, and distribute. See [LICENSE](LICENSE) for details.
