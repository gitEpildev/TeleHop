# TeleHop

Cross-server teleportation suite for **Paper 1.21+ and Velocity 3.3+** networks. Every teleport feature works seamlessly across servers, powered by MySQL and a custom plugin messaging protocol.

## Features

### Teleportation

| Feature | Commands | Description |
|---------|----------|-------------|
| **Network Spawn** | `/spawn` | Teleports players to the configured hub server. Region-aware: EU players go to `lobby-eu`, US players to `lobby-usa`. |
| **Random Teleport** | `/rtp` | Opens a GUI to pick a region and dimension, then finds a safe landing spot within the configured radius. Cooldown and warmup supported. |
| **Admin Teleport** | `/tp`, `/tphere` | Cross-server admin TP. Supports player-to-player, coordinate-based (`/tp <x> <y> <z>`), and pull (`/tphere <player>`). |

### Homes

| Feature | Commands | Description |
|---------|----------|-------------|
| **Named Homes** | `/home`, `/sethome <name>`, `/delhome <name>` | Up to 10 named homes per player, gated by `telehop.homes.1` through `telehop.homes.10` permissions. |
| **Homes GUI** | `/home` (no args) | 5-row chest GUI with configurable bed colours. Click an empty bed to set a home, click an occupied bed to teleport, shift-click to delete. |
| **Last Location** | `/lastlocation`, `/lastloc`, `/ll` | Persistent logout location tracking. Saved to MySQL on quit, survives restarts. Cross-server teleport back to where you last logged out. |

### TPA

| Feature | Commands | Description |
|---------|----------|-------------|
| **Teleport Ask** | `/tpa <player>`, `/tpahere <player>` | Request to teleport to a player or pull them to you. Works across servers. |
| **Accept / Deny** | `/tpaaccept`, `/tpadeny`, `/tpacancel` | Clickable accept/deny buttons in chat. Configurable timeout, cooldown, and warmup. |
| **TPA Toggle** | `/tpatoggle` | Block incoming TPA requests. Cross-server aware (sender gets notified even from a different server). |

### Back

| Feature | Commands | Description |
|---------|----------|-------------|
| **Back** | `/back` | Return to your last location before any teleport. Cross-server. Session-only. |
| **Death Back** | `/back death` | Return to your last death location. Cross-server. Session-only. |

### Warps

| Feature | Commands | Description |
|---------|----------|-------------|
| **Admin Warps** | `/warp`, `/setwarp`, `/delwarp`, `/warps` | Global warps shared across all servers via MySQL. Per-warp access permissions supported. |
| **Player Warps** | `/pwarp set`, `/pwarp del`, `/pwarp list`, `/pwarp public` | Personal warps with per-rank limits (`telehop.warps.1` through `telehop.warps.100`), public/private toggle, cross-server teleportation. |

### Server Features

| Feature | Description |
|---------|-------------|
| **Random Respawn** | Players respawn at a random safe location on death instead of world spawn. Async HeightMap-based search. Respects beds and anchors. Configurable per-server. |
| **Teleport Effects** | Configurable particles and sounds per teleport type (spawn, tpa, rtp, warp, home, back). |
| **Multi-Proxy** | Opt-in Redis-based communication between multiple Velocity proxies. TPA, warps, homes, and player lists all work across proxy boundaries. |
| **Feature Toggles** | Enable/disable any module per server via `features.yml`. Disabled commands still register but display "This feature is disabled." |
| **Multi-Language** | 6 built-in languages (en, nl, de, es, zh, pl) with automatic English fallback for missing keys. |

## Commands

### Spawn

| Command | Description | Permission |
|---------|-------------|------------|
| `/spawn` | Teleport to the network spawn (hub server) | `telehop.spawn` |

### Homes

| Command | Description | Permission |
|---------|-------------|------------|
| `/home` | Open the homes GUI | `telehop.homes` |
| `/home <name>` | Quick-teleport to a named home | `telehop.homes` |
| `/sethome <name>` | Set a named home at your current location | `telehop.sethome` |
| `/delhome <name>` | Delete a named home | `telehop.delhome` |
| `/lastlocation` | Teleport to your last logout location | `telehop.lastlocation` |

Aliases: `/lastloc`, `/backlast`, `/ll`

Homes are blocked on servers listed in `home.yml > blocked-servers` (e.g. lobby). Players can still open the GUI and teleport to existing homes from any server.

### TPA

| Command | Description | Permission |
|---------|-------------|------------|
| `/tpa <player>` | Ask to teleport TO another player | `telehop.tpa` |
| `/tpahere <player>` | Ask another player to teleport to YOU | `telehop.tpahere` |
| `/tpaaccept` | Accept an incoming request | `telehop.tpa.accept` |
| `/tpadeny` | Deny an incoming request | `telehop.tpa.deny` |
| `/tpacancel` | Cancel your outgoing request | `telehop.tpa.cancel` |
| `/tpatoggle` | Toggle incoming TPA requests on/off (session-only) | `telehop.tpa.toggle` |

### Back

| Command | Description | Permission |
|---------|-------------|------------|
| `/back` | Return to your last location before a teleport | `telehop.back` |
| `/back death` | Return to your last death location | `telehop.back.death` |

Both commands work cross-server. Locations are session-only (not persisted across restarts).

### Random Teleport

| Command | Description | Permission |
|---------|-------------|------------|
| `/rtp` | Random teleport — opens region/dimension GUI | `telehop.rtp` |

### Admin Warps

| Command | Description | Permission |
|---------|-------------|------------|
| `/warp <name>` | Teleport to a global warp | `telehop.warp` |
| `/setwarp <name>` | Create or update a global warp | `telehop.admin` |
| `/delwarp <name>` | Delete a global warp | `telehop.admin` |
| `/warps` | List all global warps | `telehop.warp` |

### Player Warps

| Command | Description | Permission |
|---------|-------------|------------|
| `/pwarp set <name>` | Create a personal warp at your location | `telehop.pwarp` |
| `/pwarp del <name>` | Delete one of your warps | `telehop.pwarp` |
| `/pwarp list` | List your warps with count/limit | `telehop.pwarp` |
| `/pwarp <name>` | Teleport to your own warp | `telehop.pwarp` |
| `/pwarp <player> <name>` | Teleport to another player's public warp | `telehop.pwarp` |
| `/pwarp public <name>` | Toggle a warp between public and private | `telehop.pwarp` |
| `/pwarp admin del <player> <name>` | Admin: delete any player's warp | `telehop.admin` |

Aliases: `/playerwarp`, `/pwarps`

### Admin Teleport

| Command | Description | Permission |
|---------|-------------|------------|
| `/tp <player>` | Teleport yourself to a player (cross-server) | `telehop.tp` |
| `/tp <p1> <p2>` | Teleport player p1 to player p2 (cross-server) | `telehop.tp` |
| `/tp <x> <y> <z>` | Teleport yourself to coordinates | `telehop.tp` |
| `/tp <player> <x> <y> <z>` | Teleport a player to coordinates (cross-server) | `telehop.tp` |
| `/tphere <player>` | Pull a player to your location (cross-server) | `telehop.tphere` |

Hidden from tab complete for players without the required permission.

### Admin Management

| Command | Aliases | Description | Permission |
|---------|---------|-------------|------------|
| `/telehop help` | `/telehop` | Categorised command reference | Everyone |
| `/telehop version` | `/telehop ver` | Show plugin version | Everyone |
| `/telehop reload` | | Reload config, messages, and warp cache | `telehop.admin` |
| `/telehop perms` | `/telehop permissions` | List all permission nodes | `telehop.admin` |
| `/listwarps` | | List all player warps across all servers | `telehop.admin` |
| `/listwarps <player>` | | List a specific player's warps | `telehop.admin` |
| `/forcedelwarp <name>` | | Force-delete an admin warp | `telehop.admin` |
| `/forcedelwarp <player> <name>` | | Force-delete a player's warp | `telehop.admin` |
| `/forcedelhome <player>` | | List a player's homes with clickable delete buttons | `telehop.admin` |
| `/forcesethome <player> <name>` | | Set a home for another player at your location | `telehop.admin` |
| `/listhomes <player>` | | List a player's homes with [TP] and [DELETE] buttons | `telehop.admin` |
| `/forcelastloc <player>` | `/forcell` | View a player's last logout location | `telehop.admin` |
| `/forcelastloc <player> tp` | | Teleport to a player's last logout location | `telehop.admin` |
| `/forcelastloc <player> clear` | | Clear a player's saved logout location | `telehop.admin` |
| `/playerinfo <player>` | `/pinfo` | View a player's TeleHop data summary | `telehop.admin` |

Admin-only subcommands are hidden from tab complete for players without `telehop.admin`.

## Permissions

### Player Permissions (default: everyone)

| Permission | Command |
|------------|---------|
| `telehop.spawn` | `/spawn` |
| `telehop.rtp` | `/rtp` |
| `telehop.tpa` | `/tpa` |
| `telehop.tpahere` | `/tpahere` |
| `telehop.tpa.accept` | `/tpaaccept` |
| `telehop.tpa.deny` | `/tpadeny` |
| `telehop.tpa.cancel` | `/tpacancel` |
| `telehop.tpa.toggle` | `/tpatoggle` |
| `telehop.warp` | `/warp`, `/warps` |
| `telehop.pwarp` | `/pwarp` |
| `telehop.homes` | `/home` |
| `telehop.sethome` | `/sethome` |
| `telehop.delhome` | `/delhome` |
| `telehop.lastlocation` | `/lastlocation` |
| `telehop.back` | `/back` |
| `telehop.back.death` | `/back death` |

### Limit Permissions (assign ONE per rank)

| Permission | Effect |
|------------|--------|
| `telehop.homes.1` through `telehop.homes.10` | Number of home slots |
| `telehop.warps.1` through `telehop.warps.100` | Number of player warp slots |
| `telehop.warps.unlimited` | No player warp limit |
| `telehop.warp.<name>` | Access to a specific admin warp |

### Bypass Permissions (default: OP)

| Permission | Effect |
|------------|--------|
| `telehop.rtp.bypasscooldown` | Skip RTP cooldown |
| `telehop.rtp.bypassdelay` | Skip RTP warmup countdown |
| `telehop.tpa.bypasscooldown` | Skip TPA cooldown |

### Admin Permissions (default: OP)

| Permission | Effect |
|------------|--------|
| `telehop.admin` | All admin commands (`/setwarp`, `/delwarp`, `/forcedelhome`, `/forcesethome`, `/listhomes`, `/forcelastloc`, `/playerinfo`, `/listwarps`, `/forcedelwarp`, `/telehop reload`, `/telehop perms`) |
| `telehop.tp` | `/tp` (cross-server admin teleport) |
| `telehop.tphere` | `/tphere` (pull player to you) |

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
2. Place `telehop-velocity-2.0.0.jar` on your Velocity proxy
3. Place `telehop-paper-2.0.0.jar` on each Paper backend
4. Edit `plugins/TeleHop/config/database.yml` with your MySQL credentials
5. Edit `plugins/TeleHop/config/general.yml` — set `server-name` on each server and `hub-server` to your lobby
6. Restart Velocity first, then all Paper servers

See [docs/setup.md](docs/setup.md) for the full walkthrough.

## Configuration

TeleHop uses modular config files in `plugins/TeleHop/config/`:

```
plugins/TeleHop/
  config/
    general.yml       # server-name, hub-server, servers list, language, messaging, regions
    database.yml      # MySQL connection settings
    features.yml      # feature toggles (spawn, rtp, tpa, warps, homes, back, tpa-toggle, last-location, random-respawn)
    teleport.yml      # particles and sounds per teleport type
    tpa.yml           # timeout, cooldown, delay, cancel-on-move
    rtp.yml           # cooldown, delay, max-radius, regions, dimensions, GUI
    home.yml          # max-slots (10), gui-rows (5), bed colours, blocked servers, world/server colours
    respawn.yml       # random respawn world, radius, bed/anchor respect
    WIKI.md           # full configuration reference (auto-extracted)
  storage.yml         # runtime-mutable spawn location (written by /setspawn)
  languages/          # en.yml, nl.yml, de.yml, es.yml, zh.yml, pl.yml
```

Most settings reload live with `/telehop reload`. MySQL connection settings require a full server restart.

## Database

Tables are created automatically on first startup. See [`sql/schema.sql`](sql/schema.sql) for the full schema.

| Table | Purpose |
|-------|---------|
| `players` | Tracks which server each player is on |
| `warps` | Admin warps (name, location, server) |
| `player_warps` | Player warps (owner, name, location, public/private) |
| `tpa_requests` | Active TPA requests with sent_at timestamp |
| `homes` | Player homes (uuid, name, server, world, coordinates) |
| `last_locations` | Persistent logout locations (uuid, server, world, coordinates) |

## Documentation

| Guide | Description |
|-------|-------------|
| [Setup](docs/setup.md) | Installation, MySQL, Velocity + Paper configuration |
| [Commands](docs/commands.md) | Every command with syntax, description, and permission |
| [Permissions](docs/permissions.md) | All permission nodes, defaults, and LuckPerms examples |
| [Configuration](docs/configuration.md) | Full config reference for Paper and Velocity |
| [Homes](docs/homes.md) | Homes GUI, slots, blocked servers, world/server colours |
| [Warps](docs/warps.md) | Admin warps vs player warps, limits, public/private |
| [Messages](docs/messages.md) | Language system, all message keys, MiniMessage formatting |
| [Protocol](docs/protocol.md) | Plugin messaging protocol, packet types, routing |
| [Troubleshooting](docs/troubleshooting.md) | Common issues and fixes |

## Upgrading from 1.0.0

Drop in the new 2.0.0 JARs (Paper + Velocity). The `homes` table is migrated automatically on first startup (slot-based to name-based). Existing homes are renamed "Home 1", "Home 2", etc. Grant `telehop.homes.2` through `telehop.homes.10` to ranks via LuckPerms for additional home slots. If you had a pre-split `config.yml`, the plugin auto-migrates it to the `config/` directory layout.

## Building

```bash
mvn clean package
```

Produces:
- `paper/target/telehop-paper-2.0.0.jar`
- `velocity/target/telehop-velocity-2.0.0.jar`

Requires Java 21+ and Maven 3.8+.

## CI / CD

| Workflow | Trigger | What it does |
|----------|---------|--------------|
| `ci.yml` | push/PR to `main` | Checkstyle, unit tests, JaCoCo coverage, JAR build |
| `pr.yml` | PR to `main` | Conventional Commits title check, artifact check, auto-labels |
| `release.yml` | push tag `v*.*.*` | Validates version, builds, creates GitHub Release with JARs |
| `codeql.yml` | push/PR + weekly | CodeQL static security analysis |

## Author

**Epildev** — [GitHub](https://github.com/GitEpildev) · [Website](https://developer.epildevconnect.uk/) · Discord: `Epildev`

Developed by [Epildevconnect Ltd](https://developer.epildevconnect.uk/myhub/home).

## License

MIT License — free to use, modify, and distribute. See [LICENSE](LICENSE) for details.
