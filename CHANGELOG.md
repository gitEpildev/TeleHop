# Changelog

All notable changes to TeleHop are documented here.  
Format follows [Keep a Changelog](https://keepachangelog.com/).

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
