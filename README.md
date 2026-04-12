# TeleHop

Cross-server teleportation plugin for **Paper + Velocity** networks.

## Features

- **Network Spawn** `/spawn` — sends players to the configured hub server, cross-server
- **Admin Warps** `/warp`, `/setwarp`, `/delwarp`, `/warps` — shared across all servers via MySQL
- **Player Warps** `/pwarp` — personal warps with per-rank limits, public/private toggle, cross-server
- **TPA** `/tpa`, `/tpahere`, `/tpaaccept`, `/tpadeny`, `/tpacancel` — works across servers
- **TPA Toggle** `/tpatoggle` — block incoming TPA requests, cross-server aware
- **Random Teleport** `/rtp` — GUI region + dimension picker, safe landing, configurable radius
- **Homes** `/home`, `/sethome` — GUI with configurable bed colours, permission-based slots (1–5), blocked servers, cross-server
- **Back** `/back`, `/back death` — return to last teleport or death location, cross-server
- **Random Respawn** — async safe-location search on death, applied at respawn; respects beds and anchors; automatically skipped on the hub server; applies to all players unconditionally
- **Admin Teleport** `/tp`, `/tphere` — cross-server admin TP
- **Teleport Effects** — configurable particles and sounds per teleport type (spawn, tpa, rtp, warp, home, back)
- **Feature Toggles** — enable/disable any module per server without removing commands
- **Multi-Language** — 6 built-in languages (en, nl, de, es, zh, pl) with automatic English fallback
- **Admin Tools** — `/telehop reload/version/perms/help`, `/listwarps`, `/forcedelwarp`, `/forcedelhome`
- **Tab Completion** — player and warp names autocomplete across the entire network
- **Modular Configuration** — split config files under `config/` with auto-migration from legacy `config.yml`

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
2. Place `telehop-velocity-1.0.2.jar` on your Velocity proxy
3. Place `telehop-paper-1.0.2.jar` on each Paper backend
4. Edit `plugins/TeleHop/config/database.yml` with your MySQL credentials
5. Edit `plugins/TeleHop/config/general.yml` — set `server-name` on each server and `hub-server` to your lobby
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

A full in-editor configuration reference is also bundled with the plugin itself at `plugins/TeleHop/config/WIKI.md` — extracted automatically on first run.

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
    features.yml      # feature toggles (spawn, rtp, tpa, warps, homes, back, tpa-toggle, random-respawn...)
    teleport.yml      # show-countdown, particles & sounds per teleport type
    tpa.yml           # timeout, cooldown, delay, cancel-on-move
    rtp.yml           # cooldown, delay, max-radius, regions, dimensions, GUI
    home.yml          # max-slots, confirm-set, GUI, beds, blocked-servers, world/server colours
    respawn.yml       # random respawn world, radius, bed/anchor respect options
    WIKI.md           # full configuration reference (auto-extracted, human-readable)
  storage.yml         # runtime-mutable spawn location
  languages/          # en.yml, nl.yml, de.yml, es.yml, zh.yml, pl.yml
```

> **Upgrading from 1.0.0 / 1.0.1:** Drop in the new JAR — TeleHop extracts only new files (`respawn.yml`, `WIKI.md`) without touching your existing config. Add `random-respawn: true` to `features.yml` to explicitly enable random respawn (defaults to `true` even without the key).

> **Upgrading from a pre-split config.yml:** If `config.yml` exists but `config/general.yml` does not, the plugin automatically migrates the single file into the split layout and renames it to `config.yml.old`.

## Project Structure

```
telehop-plugin/
├── common/                Shared code (DB, models, services)
├── paper/                 Paper backend plugin
│   └── src/.../paper/
│       ├── Bootstrap.java              Startup wiring + config migration
│       ├── NetworkPaperPlugin.java     Thin lifecycle entry point
│       ├── config/
│       │   ├── PaperSettings.java      Immutable config record (loads from split YAMLs)
│       │   ├── ConfigMigrator.java     One-time config.yml → config/ migration
│       │   └── StorageManager.java     Runtime-mutable values (storage.yml)
│       ├── handler/
│       │   └── PacketHandler.java      Cross-server packet dispatch
│       ├── listener/
│       │   ├── PaperPlayerListener.java  Join/quit/death/respawn events (hub spawn)
│       │   └── RespawnListener.java      Random respawn — async pre-stages safe location on death
│       ├── command/
│       │   ├── tpa/                    TPA commands + TpaToggleCommand
│       │   ├── warp/                   Warp + player-warp commands
│       │   ├── home/                   HomeCommand, SetHomeCommand
│       │   ├── admin/                  Admin commands (/telehop, /tp, /tphere, /forcedelhome)
│       │   ├── SpawnCommand.java
│       │   ├── RtpCommand.java
│       │   └── BackCommand.java
│       ├── service/
│       │   ├── ServiceRegistry.java        Central service holder
│       │   ├── TeleportService.java        Spawn, warp, RTP, home, back + effects
│       │   ├── TeleportEffectPlayer.java   Particles & sounds per teleport type
│       │   ├── RandomRespawnManager.java   Thread-safe one-shot location staging for random respawn
│       │   ├── RandomRespawnService.java    Async safe-location search for death respawn (HeightMap-based)
│       │   ├── RtpManager.java             Safe location search for /rtp command
│       │   ├── BackLocationManager.java    In-memory /back locations (session-only)
│       │   ├── TpaRuntimeManager.java      TPA requests, cooldowns, toggle state
│       │   ├── MessageService.java         Language keys + MiniMessage deserialisation
│       │   └── ...
│       └── gui/
│           ├── HomeGui.java       Homes chest GUI (configurable beds, sub-menus)
│           └── RtpGui.java        RTP region/dimension picker
├── velocity/              Velocity proxy plugin
│   └── src/.../velocity/
│       ├── VelocityBootstrap.java             Startup wiring
│       ├── NetworkVelocityPlugin.java         Thin lifecycle entry point
│       ├── config/
│       │   └── VelocitySettings.java          Immutable config record
│       ├── handler/
│       │   └── VelocityPacketHandler.java     Cross-server packet dispatch + routing
│       ├── service/
│       │   ├── VelocityServiceRegistry.java   Central service holder
│       │   ├── VelocityPlayerTracker.java     Player-to-server mapping
│       │   └── PendingActionManager.java      Queued post-transfer actions
│       ├── messaging/
│       │   └── VelocityMessagingManager.java  Plugin-message channel
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
- `paper/target/telehop-paper-1.0.2.jar`
- `velocity/target/telehop-velocity-1.0.2.jar`

Requires Java 21+ and Maven 3.8+.

## CI / CD

GitHub Actions runs on every push and PR to `main` across four workflows:

| Workflow | Trigger | What it does |
|----------|---------|--------------|
| `ci.yml` | push/PR → `main` | Checkstyle, unit tests with JaCoCo coverage report, JAR build; uploads JARs on `main` push |
| `pr.yml` | PR → `main` | Validates PR title (Conventional Commits), checks no build artifacts committed, warns on large diffs, auto-labels |
| `release.yml` | push tag `v*.*.*` | Validates tag matches `pom.xml` version, builds, creates GitHub Release with JARs attached |
| `codeql.yml` | push/PR + weekly | CodeQL static security analysis (Java) |

## Testing

Unit tests live in `common/src/test/` and cover all core service and cache classes.

| Test class | Coverage |
|------------|---------|
| `WarpServiceTest` | Caching, DB interaction, list sorting |
| `TpaServiceTest` | Upsert, find (cache hit/miss), delete, expiry |
| `WarpCacheTest` | put/get (case-insensitive), remove, replaceAll, list |
| `TpaRequestCacheTest` | Directional keys, overwrite, remove, expired filtering |
| `PlayerWarpServiceTest` | All 8 service methods including findPublic and setPublic |
| `HomeServiceTest` | Async delegation to repository layer (mocked) |
| `DatabaseConfigTest` | Input validation, JDBC URL generation |
| `DatabaseCircuitBreakerTest` | CLOSED → OPEN → HALF_OPEN state transitions |
| `PacketCodecTest` | JSON encode/decode round-trips, payload preservation |
| `RequestTrackerTest` | Future tracking, timeout, deduplication |

```bash
mvn test -pl common
```

91 tests, 0 failures.

## Author

**Epildev** — [GitHub](https://github.com/GitEpildev) · [Website](https://developer.epildevconnect.uk/myhub/home) · Discord: `Epildev`

Developed by [Epildevconnect Ltd](https://developer.epildevconnect.uk/myhub/home).

## License

MIT License — free to use, modify, and distribute. See [LICENSE](LICENSE) for details.
