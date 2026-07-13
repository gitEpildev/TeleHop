# Changelog

All notable changes to TeleHop are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/).

---

## [Unreleased]

---

## [2.1.0] - 2026-07-13

### Added

**Redis TLS**
- `redis.ssl` toggle in `config.properties` to connect to Redis over TLS (rediss)
- `redis.ssl-verify` to control certificate and hostname verification
- `redis.ssl-ca-cert` for self-signed setups (path to a PEM CA certificate)

**Destination Ping in RTP GUI**
- Region items now show the ping a player will get on the destination server, with the value colour-coded green/yellow/red
- The ping line refreshes twice per second while the menu is open, including while hovering
- Velocity measures proxy-to-backend RTT every 10 seconds and broadcasts it via the new `SERVER_PING_UPDATE` packet
- Paper-side `PingService` caches the RTTs and estimates post-transfer ping from the player's current connection
- New `rtp.gui.ping` section in `rtp.yml`: `enabled`, `proxy-ping`
- GUI item tooltips hide vanilla attribute/material info (item flags applied)

### Fixed

**RTP**
- The picker GUI closes immediately when a destination is selected, so players can no longer re-click items to restart or stack the warmup countdown
- Cooldown is now consumed when a destination is actually selected, not when `/rtp` opens the menu; opening and closing the GUI no longer burns the cooldown
- A pending-RTP guard blocks `/rtp` and further selections while a warmup or transfer is in flight (new `rtp-in-progress` message in all six languages)

### Migration from 2.0.0
- Update the Paper and Velocity JARs together: 2.0.0 jars do not recognise the new `SERVER_PING_UPDATE` packet
- Existing servers: add the `rtp.gui.ping` section to `config/rtp.yml` and the `rtp-in-progress` key to customised language files (or regenerate them); defaults and the English fallback apply otherwise
- No database changes

---

## [2.0.0] - 2026-06-01

### Added

**Version Adapters**
- Multi-version support: a single JAR runs on Paper 1.21.x (Java 21) and Paper 26.1.x (Java 25)
- `VersionAdapter` interface abstracts `World.getName()` and `Bukkit.getWorld()` across API versions
- `Paper121Adapter` for 1.21.x (standard name-based world resolution)
- `Paper261Adapter` for 26.1.x (key-based world resolution with name fallback)
- Runtime version detection in `Bootstrap` using `getMinecraftVersion()` with Bukkit version string fallback
- Adapters loaded via `Class.forName` reflection to avoid `NoClassDefFoundError` on unsupported versions
- CI verification that both adapter modules compile against their respective Paper API versions

**Homes**
- Named homes system: `/sethome <name>`, `/home <name>`, `/delhome <name>`
- Up to 10 homes per player, gated by `telehop.homes.1` through `telehop.homes.10` permissions
- 5-row GUI with configurable bed colours, world/server labels, and clickable set/delete
- Home names are case-insensitive, alphanumeric with spaces allowed, max 32 characters
- Blocked servers and blocked server prefixes to restrict where homes can be set
- Cross-server home teleportation via Velocity routing

**Back and Last Location**
- `/back` and `/back death` to return to last teleport or death location (session-only, cross-server)
- `/lastlocation` (aliases: `/lastloc`, `/backlast`, `/ll`) for persistent logout location tracking
  - Saved on quit, persisted in MySQL `last_locations` table
  - Cross-server support with automatic server transfer

**TPA Toggle**
- `/tpatoggle` to block incoming TPA requests (session-only, cross-server aware)

**Random Respawn**
- Players respawn at a random safe location on death instead of world spawn
- Fully async safe-location search using Paper's HeightMap API
- Configurable via `config/respawn.yml` (world, radius, bed/anchor respect)
- Automatically skipped on the hub server

**Multi-Proxy Support**
- Opt-in Redis-based cross-proxy communication for multiple Velocity proxies
- TPA requests, admin teleports, transfers, and player lists all work across proxy boundaries
- Region-aware `/spawn` routing maps servers to regional hubs (e.g. EU players go to `lobby-eu`)
- Configurable via `multi-proxy.enabled`, `proxy.id`, Redis connection in `config.properties`

**Coordinate Teleportation**
- `/tp <x> <y> <z>` and `/tp <player> <x> <y> <z>` with cross-server support

**Admin Commands**
- `/forcedelhome <player>` to list and delete a player's homes
- `/forcesethome <player> <name>` to set a home for another player
- `/listhomes <player>` with clickable [TP] and [DELETE] buttons
- `/forcelastloc <player>` (alias `/forcell`) to view, teleport to, or clear a player's last logout location
- `/playerinfo <player>` (alias `/pinfo`) to view a player's TeleHop data summary
- `/forcedelwarp <name>` and `/forcedelwarp <player> <name>` for admin warp management
- `/listwarps [player]` to list all player warps across servers

**RTP GUI**
- Back button (spectral arrow) in the dimension picker to return to region selection

**Teleport Effects**
- Configurable particles and sounds per teleport type (spawn, tpa, rtp, warp, home, back)

**Configuration**
- Modular config split: `config/general.yml`, `database.yml`, `features.yml`, `teleport.yml`, `tpa.yml`, `rtp.yml`, `home.yml`, `respawn.yml`
- Auto-migration from legacy monolithic `config.yml`
- Configuration wiki (`WIKI.md`) bundled in `plugins/TeleHop/config/` on first run

**In-Game Help**
- `/telehop help` with categorised command listing (general, TPA, homes, back, warps, admin)
- `/telehop perms` with all permission nodes and descriptions

**CI/CD**
- GitHub Actions: CI (checkstyle, tests, build), PR checks, release workflow, CodeQL security scanning
- Version consistency checks across pom.xml, plugin.yml, and Velocity @Plugin
- YAML syntax validation and language key parity checks
- Tab completion works across the entire network (player names + warp names)
- Permission-gated tab complete: `/tp`, `/tphere`, admin subcommands hidden from non-admins

### Changed (from 1.0.0)
- Homes migrated from slot-based `(uuid, slot INT)` to name-based `(uuid, name VARCHAR)` schema
- Cross-server home packets use `homeName` instead of `homeSlot`
- `home.yml` defaults: `max-slots: 10`, `gui-rows: 5`
- `SpawnCommand` uses region-aware hub resolution
- `AdminTeleportCommand` supports coordinate forms alongside player-name modes
- Jedis 5.1.0 added as a dependency (shaded in Velocity JAR) for multi-proxy support

### Migration from 1.0.0
- Drop in the new 2.0.0 JARs (Paper + Velocity)
- Homes table is migrated automatically on first startup (slot numbers converted to names)
- No manual database intervention needed
- Grant `telehop.homes.N` (2-10) to ranks via LuckPerms for additional home slots
- If upgrading from a pre-split `config.yml`, the plugin auto-migrates to the `config/` directory layout

---

## [1.0.0] - 2026-04-08

### Added
- **Network Spawn** - `/spawn` sends players to the hub server, cross-server
- **Admin Warps** - `/warp`, `/setwarp`, `/delwarp`, `/warps` shared via MySQL
- **Player Warps** - `/pwarp` with per-rank limits, public/private toggle, cross-server
- **TPA** - `/tpa`, `/tpahere`, `/tpaaccept`, `/tpadeny`, `/tpacancel` across servers
- **Random Teleport** - `/rtp` with region/dimension picker GUI
- **Admin Teleport** - `/tp`, `/tphere` cross-server
- **Feature Toggles** - enable/disable any module per server
- **Multi-Language** - 6 built-in languages (en, nl, de, es, zh, pl) with fallback
- **Cross-server messaging** - plugin messaging channel with deduplication and timeout
- MySQL shared database for warps, player warps, TPA requests, and player tracking
