# Changelog

All notable changes to TeleHop are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/).

---

## [4.0.0] - 2026-11-27

### Added

- Choose a standalone, multi-proxy or custom deployment profile. Both plugins use YAML and keep database credentials separate from the active profile. Existing settings have a migration history and private recovery copies.
- The Bukkit backend file installs on supported Paper, Spigot, CraftBukkit and Purpur servers. Replace the previous backend file when upgrading. The proxy still uses the matching Velocity file.
- Browse the backend configuration in the same folders used on the server. The root configuration and bundled setup guide explain which files to edit. Upgrades refresh earlier stock guides and preserve local edits.
- Configure command names by category and define additional random teleport, named spawn and hub commands. A command can require permissions for its own identifier and its destination. Global warp lists honour individual warp permissions.
- Inspect the detected platform, validate configuration and view migration details in game. The backend selects available teleport and chunk operations at startup.
- Release checks exercise the published previous version, multiple database engines and a network containing different backend implementations. The release includes a checksum for each plugin file.

### Fixed

- Proxy travel checks the player's current connection and the destination before applying a configured command. Duplicate forwarded requests are discarded and the player list collects replies from multiple proxies.
- Fresh installations can search the world where a player died for a safe respawn. Upgrades retain existing world exclusions and destinations. A delayed result from an earlier death cannot move the player again.

### Upgrade

Back up the shared database and both plugin folders. Install matching 4.0.0 files, review the selected profile and check a saved home and spawn with a member account. [Upgrade and recovery](https://github.com/gitEpildev/TeleHop/blob/v4.0.0/docs/migration.md)

## [3.0.1] - 2026-11-25

### Fixed

- The optional hub command now appears in the Paper feature list. Set `features.hub` in `config/features.yml` and restart Paper to register the command and its aliases.
- Upgrades add missing command names and feature switches to existing files. They retain configured aliases, disabled features and the previous hub setting. The help menu shows the hub command by its configured name when it is registered.
- Reloading routes keeps the shared spawn directory in step with the command registered on each backend.
- The rollback check now starts the published 2.3.0 backend with its own command settings and verifies that players can return using `/spawn`.
- GitHub builds can download release dependencies from a second Maven Central endpoint when shared runners encounter download limits.

### Upgrade

Replace both plugin builds with 3.0.1 and restart the network. On each Paper backend, check `features.hub` in `config/features.yml` and the names under `hub` in `config/commands.yml`. New installations leave the command disabled by default. Players with `telehop.hub` can use the configured name after the next Paper start. [Hub setup](https://github.com/gitEpildev/TeleHop/blob/v3.0.1/docs/spawns.md#enable-the-hub-command)

## [3.0.0] - 2026-11-24

### Added

- Members can select a spawn by its configured name across worlds and servers. Owners can set a default route for a backend or a single world and enable a separate hub command.
- Staff can delegate home inspection, visits, removal and player teleport commands separately. Restricted destinations support individual permission grants.
- Owners can add, rename or remove a destination in YAML and reload it in game. MySQL stores the complete locations, access rules and routes for each backend.
- The proxy build recognises Velocity 3 and 4. The runtime checks cover both generations.
- Release checks start every published Paper server version in the supported 1.21 series, plus 26.1.2 and 26.2. A Minecraft 1.21.2 client connects through Velocity to Paper 1.21.3.

### Fixed

- The Paper build now uses Java 21 class files throughout so older Paper servers can remap the entire plugin. Newer Paper servers still run on Java 25. Configured teleport sounds now load across the full 1.21 series.
- Players sharing a backend can travel through different proxies. Named spawn requests and replies follow the traveller's connection, including when another player joined the backend first.
- A rejected transfer clears its pending teleport. Returning to the backend later no longer repeats the failed action.
- Conversion of an old Paper configuration can resume after an interruption. Existing split files, saved spawns and earlier backups are preserved.
- Concurrent server starts now share one database upgrade at a time. A conflicting legacy home name stops the upgrade and preserves the original slot for recovery.
- Visiting a listed home uses the selected owner's location. Existing language files inherit new messages in the selected language.

### Upgrade

Replace the Paper and Velocity builds together. The existing spawn location and regional hub remain the defaults. Follow the [spawn guide](docs/spawns.md) to enable routing and grant staff permissions.

## [2.3.0] - 2026-11-23

### Added

- Paper command names and aliases can now be changed in `plugins/TeleHop/config/commands.yml`. Help text and clickable teleport actions use the configured names.
- Separate permission overrides for teleporting yourself or other players to coordinates.
- Manual and tag-triggered publication of matching Paper and Velocity builds to GitHub and Modrinth. See the [release guide](https://github.com/gitEpildev/TeleHop/blob/v2.3.0/docs/releases/publishing.md).
- Release checks now require a complete build and a dated changelog entry matching the requested version. GitHub checks UK English spelling and punctuation before publication.

### Fixed

- Paper servers without LuckPerms now start with Bukkit permissions.

### Upgrade

Replace the TeleHop JAR on each Paper backend and Velocity proxy with its matching 2.3.0 build and restart the instance. Paper creates `plugins/TeleHop/config/commands.yml` on startup. To free `/tp` for another plugin, set `tp: [networktp, ntp]` and restart Paper. To keep player-to-player teleportation while restricting coordinates, grant `telehop.tp` and explicitly deny `telehop.tp.coordinates.self` or `telehop.tp.coordinates.others`.

### Documentation
- 4 November 2026: reconcile this standalone repository with the existing 2.2.0 network release. Correct language upgrade instructions and document recorded deployment and verification limits.

---

## [2.2.0] - 2026-10-19

### Changed
- Teleport warmups show a large green centred title, counting down once per second.
- Removed repeated countdown numbers from chat and the action bar.
- The existing `show-countdown` setting controls the title. Completion and movement cancellation clear it.
- Added `countdown-title` translations in all six bundled languages.

### Upgrade from 2.1.0
- Update Paper and Velocity JARs to 2.2.0, preserving existing configuration, storage and databases.
- No database migration or protocol change. All 2.1.0 functionality is retained.
- Existing language files remain intact. Add `countdown-title` to customised language files, including the on-disk English fallback; missing bundled keys are not merged automatically.
- Existing zero-second warmups and delay-bypass permissions remain unchanged.

---

## [2.1.0] - 2026-07-13

### Added

**Redis TLS**
- `redis.ssl` toggle in `config.properties` to connect to Redis over TLS (`rediss`)
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
- Existing servers: add the `rtp.gui.ping` section to `config/rtp.yml` and the `rtp-in-progress` key to customised language files, preserving existing customisations; fallback requires the key in the on-disk English file
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
- Fully async safe-location search using Paper's `HeightMap` API
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
- Version consistency checks across `pom.xml`, `plugin.yml` and Velocity `@Plugin`
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
- If upgrading from an older single-file `config.yml`, the plugin auto-migrates to the `config/` directory layout

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
- **Multi-Language** - 6 built-in languages (`en`, `nl`, `de`, `es`, `zh`, `pl`) with fallback
- **Cross-server messaging** - plugin messaging channel with deduplication and timeout
- MySQL shared database for warps, player warps, TPA requests, and player tracking
