# Changelog

All notable changes to TeleHop are documented here.  
Format follows [Keep a Changelog](https://keepachangelog.com/).

---

## [Unreleased]

---

## [1.1.0] — 2026-05-03

### Added
- **Multi-proxy support** — opt-in Redis-based cross-proxy communication so two or more Velocity proxies can share the same backend servers
  - New `RedisCrossProxyBridge` for Velocity-to-Velocity pub/sub via Redis
  - TPA requests, admin teleports, transfers, and player list queries all work across proxy boundaries
  - Player location broadcasts keep all proxies aware of every player's current server
  - Configurable via `multi-proxy.enabled`, `proxy.id`, Redis connection, and `global-player-list` in `config.properties`
  - Zero overhead when disabled — no Redis connection, no extra threads
- **Region-aware /spawn routing** — new `regions` config in `general.yml` maps servers to regional hubs
  - Players on `eu` server get sent to `lobby-eu`, players on `usa` get sent to `lobby-usa`
  - Falls back to `hub-server` if the current server isn't in any region
- **Blocked server prefixes** — `blocked-server-prefixes` in `home.yml` blocks home-setting on servers by name prefix
  - Example: `lobby` matches `lobby-usa`, `lobby-eu`, `lobby-asia`
  - Works alongside existing exact-match `blocked-servers`
- **Cross-proxy player list** — when `global-player-list` is true, `/tpa` tab-complete shows players from all proxies
- **Coordinate teleportation** — `/tp` now accepts `x y z` and `player x y z` forms
  - `/tp <x> <y> <z>` — teleports the sender to coordinates in their current world
  - `/tp <player> <x> <y> <z>` — teleports a named player to coordinates; works cross-server via a new `ADMIN_TP_TO_COORDS` packet
  - Invalid coordinates produce a localised `invalid-coords` message in all 6 languages
- **Permission-gated tab complete** — `/tp` and `/tphere` are now hidden from the brigadier command tree for players without `telehop.tp` / `telehop.tphere` respectively; `telehop reload` and `telehop perms` are similarly hidden from non-admins

### Changed
- `VelocitySettings` now includes `multiProxyEnabled`, `globalPlayerList`, `crossProxyTimeoutMs`, and `RedisConfig`
- `PaperSettings` now includes `multiProxyEnabled`, `regions`, and `homeBlockedServerPrefixes`
- `SpawnCommand` uses region-aware hub resolution instead of a single `hub-server`
- `VelocityPlayerTracker` supports cross-proxy player lookups via Redis global map
- `VelocityPacketHandler` falls back to Redis forwarding when target player is not on the local proxy
- `PacketType` enum gains `ADMIN_TP_TO_COORDS`, `CROSS_PROXY_FORWARD`, `CROSS_PROXY_PLAYER_LIST`, `CROSS_PROXY_PLAYER_UPDATE`
- `AdminTeleportCommand` extended from 2 optional strings to 4 optional strings to support coordinate forms without breaking existing player-name modes
- Jedis 5.1.0 added as a dependency (shaded in Velocity JAR)

---

## [1.0.2] — 2026-04-12

### Added
- **Random Respawn** — players respawn at a random safe location on death instead of world spawn
  - Fully async safe-location search using Paper's HeightMap API — no main thread blocking
  - Configurable via `config/respawn.yml` (world, radius, bed/anchor respect)
  - Feature toggle in `features.yml` under `random-respawn`
  - Automatically skipped on the hub server
  - Applies unconditionally to all players — no bypass permission, no exceptions
- **Configuration Wiki** — `WIKI.md` extracted into `plugins/TeleHop/config/` on first run
  - Full reference for every config file, permission node, and admin command
  - Upgrade guide for migrating from previous versions
  - Multi-server setup example
- **Stricter CI pipeline**
  - Compile gate: all modules (common, paper, velocity) compiled before tests run
  - Version consistency check: pom.xml, plugin.yml, and Velocity @Plugin must match
  - YAML syntax validation: every config template and language file
  - Language key parity: all locale files must contain every key from en.yml
  - TODO/FIXME blocker: prevents unresolved notes from reaching main
  - Coverage thresholds raised to 75% overall / 80% changed files
  - PR compile check for immediate contributor feedback

### Fixed
- Random respawn failing on unexplored chunks — the sky-light check (`getLightFromSky`) returned 0 on freshly generated chunks (neighbours not loaded), rejecting all 200 attempts; replaced with Paper's `HeightMap.MOTION_BLOCKING` for reliable surface detection
- Random respawn race condition — previously reused RtpManager's sync-bounce pattern which was too slow; now uses dedicated `RandomRespawnService` with Paper async chunk loading
- If the location search finishes after the player clicks "Respawn", the player is teleported on the next tick instead of being silently dropped at world spawn

### Removed
- `telehop.respawn.bypass` permission — random respawn is now unconditional for all players on survival servers
- Legacy monolithic `config.yml` resource — split config files under `config/` are the sole source of truth; auto-migration from old `config.yml` still works for existing installs

---

## [1.0.1] — 2026-04-10

### Added
- **Homes** — `/home`, `/sethome` with GUI, configurable bed colours, permission-based slots (1–5), blocked servers, cross-server
- **Back** — `/back`, `/back death` to return to last teleport or death location
- **TPA Toggle** — `/tpatoggle` to block incoming TPA requests
- **Admin tools** — `/forcedelhome`, `/forcedelwarp`
- **Teleport effects** — configurable particles and sounds per teleport type (spawn, tpa, rtp, warp, home, back)
- **Modular config** — split from single `config.yml` into `config/` directory (general, database, features, teleport, tpa, rtp, home)
- Auto-migration from legacy `config.yml` to split config
- 5 additional unit test classes (WarpService, TpaService, WarpCache, TpaRequestCache, PlayerWarpService)
- GitHub Actions: CI, PR checks, release, CodeQL security scanning

### Changed
- Tab completion now works across the entire network (player names + warp names)

---

## [1.0.0] — 2026-04-08

### Added
- **Network Spawn** — `/spawn` sends players to the hub server, cross-server
- **Admin Warps** — `/warp`, `/setwarp`, `/delwarp`, `/warps` shared via MySQL
- **Player Warps** — `/pwarp` with per-rank limits, public/private toggle, cross-server
- **TPA** — `/tpa`, `/tpahere`, `/tpaaccept`, `/tpadeny`, `/tpacancel` across servers
- **Random Teleport** — `/rtp` with region/dimension picker GUI
- **Admin Teleport** — `/tp`, `/tphere` cross-server
- **Feature Toggles** — enable/disable any module per server
- **Multi-Language** — 6 built-in languages (en, nl, de, es, zh, pl) with fallback
- **Cross-server messaging** — plugin messaging channel with deduplication and timeout
- MySQL shared database for warps, player warps, TPA requests, and player tracking
