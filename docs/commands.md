# Commands

For every default root, alias, staff action and confirmation command, see the [complete command reference](../README.md#commands).

## Spawn

| Command | Description | Permission |
|---------|-------------|------------|
| `/spawn` | Follow the current world, backend or hub route | `telehop.spawn` |
| `/spawn <name>` | Visit a saved destination | `telehop.spawn`, `telehop.spawn.named` and destination access |
| `/hub` | Visit the network hub when enabled | `telehop.hub` |
| `/telehop spawn list` | Show IDs, owners and access | `telehop.admin.spawn.list` |
| `/telehop spawn set <name> <public\|restricted>`, `/telehop spawn setdefault` | Save your location | `telehop.admin.spawn.set` |
| `/telehop spawn delete <name> [revision]` | Request or confirm deletion | `telehop.admin.spawn.delete` |

## Admin Warps

| Command | Description | Permission |
|---------|-------------|------------|
| `/warp <name>` | Teleport to a global warp | `telehop.warp` or `telehop.warp.<name>` |
| `/setwarp <name>` | Create or update a global warp | `telehop.admin.warps.set` |
| `/delwarp <name>` | Delete a global warp | `telehop.admin.warps.delete` |
| `/warps` | List all global warps | `telehop.warp` |

## Player Warps

| Command | Description | Permission |
|---------|-------------|------------|
| `/pwarp set <name>` | Create a personal warp at your location | `telehop.pwarp` |
| `/pwarp del <name>` | Delete one of your warps | `telehop.pwarp` |
| `/pwarp list` | List your warps with count/limit | `telehop.pwarp` |
| `/pwarp <name>` | Teleport to your own warp | `telehop.pwarp` |
| `/pwarp <player> <name>` | Teleport to another player's public warp | `telehop.pwarp` |
| `/pwarp public <name>` | Toggle a warp between public and private | `telehop.pwarp` |
| `/pwarp admin del <player> <name>` | Admin: delete any player's warp | `telehop.admin.playerwarps.delete` |

Aliases: `/playerwarp`, `/pwarps`

## TPA (Teleport Ask)

| Command | Description | Permission |
|---------|-------------|------------|
| `/tpa <player>` | Ask to teleport TO another player | `telehop.tpa` |
| `/tpahere <player>` | Ask another player to teleport to YOU | `telehop.tpahere` |
| `/tpaaccept` | Accept an incoming request | `telehop.tpa.accept` |
| `/tpadeny` | Deny an incoming request | `telehop.tpa.deny` |
| `/tpacancel` | Cancel your outgoing request | `telehop.tpa.cancel` |

## TPA Toggle

| Command | Description | Permission |
|---------|-------------|------------|
| `/tpatoggle` | Toggle incoming TPA requests on/off (session-only, resets on logout) | `telehop.tpa.toggle` |

When a player has TPA toggled off:
- Same-server senders see: *"Player has TPA requests disabled."*
- Cross-server senders also receive the same message (routed back via Velocity)

## Homes

| Command | Description | Permission |
|---------|-------------|------------|
| `/home` | Open the homes GUI | `telehop.homes` |
| `/home <name>` | Quick-teleport to a named home | `telehop.homes` |
| `/sethome <name>` | Set a named home at your current location | `telehop.sethome` |
| `/delhome <name>` | Delete a named home | `telehop.delhome` |
| `/lastlocation` | Teleport to your last logout location | `telehop.lastlocation` |

Creating homes is blocked on backends listed in `homes.blocked-servers` or matched by `homes.blocked-server-prefixes` in `home.yml`. Players can still open the GUI and teleport to existing homes from any server.

## Back

| Command | Description | Permission |
|---------|-------------|------------|
| `/back` | Return to your last location before a teleport | `telehop.back` |
| `/back death` | Return to your last death location | `telehop.back.death` |

Both commands work cross-server. Locations are session-only (not persisted across restarts).

## RTP (Random Teleport)

| Command | Description | Permission |
|---------|-------------|------------|
| `/rtp` | Random teleport, opens region/dimension GUI | `telehop.rtp` |

Region items show the estimated ping the player will get on the destination server (configurable via `rtp.gui.ping` in `rtp.yml`). The cooldown starts when a destination is selected, not when the menu opens, and the GUI closes on selection so the warmup cannot be restarted by re-clicking.

## Admin Teleport

| Command | Description | Permission |
|---------|-------------|------------|
| `/tp <player>` | Teleport yourself to a player (cross-server) | `telehop.tp.player.self` |
| `/tp <p1> <p2>` | Teleport player p1 to player p2 (cross-server) | `telehop.tp.player.others` |
| `/tp <x> <y> <z>` | Teleport yourself to coordinates in your current world | `telehop.tp.coordinates.self` |
| `/tp <player> <x> <y> <z>` | Teleport a player to coordinates (cross-server) | `telehop.tp.coordinates.others` |
| `/tphere <player>` | Pull a player to your location (cross-server) | `telehop.tphere` |

Each form checks the effective permission before moving a player or contacting Velocity.

## Admin Warp & Home Management

| Command | Description | Permission |
|---------|-------------|------------|
| `/listwarps` | List all player warps across all servers | `telehop.admin.warps.list` |
| `/listwarps <player>` | List a specific player's warps with details | `telehop.admin.warps.list` |
| `/forcedelwarp <name>` | Force-delete an admin warp | `telehop.admin.warps.delete` |
| `/forcedelwarp <player> <name>` | Force-delete a specific player's warp | `telehop.admin.playerwarps.delete` |
| `/forcedelhome <player>` | List a player's homes with clickable delete buttons | `telehop.admin.homes.delete` |
| `/forcesethome <player> <name>` | Set a home for another player at your current location | `telehop.admin.homes.set` |
| `/listhomes <player>` | List a player's homes with actions filtered by permission | `telehop.admin.homes.list` |
| `/forcelastloc <player>` | View a player's last logout location (server, world, coords) | `telehop.admin.lastlocation.view` |
| `/forcelastloc tp <player>` | Teleport to a player's last logout location | `telehop.admin.lastlocation.visit` |
| `/forcelastloc clear <player>` | Clear a player's saved logout location | `telehop.admin.lastlocation.clear` |
| `/playerinfo <player>` | Show a summary of a player's TeleHop data (homes, warps, last loc, server) | `telehop.admin.playerinfo` |

Aliases: `/forcell` for `/forcelastloc`, `/pinfo` for `/playerinfo`

## TeleHop Admin

| Command | Description | Permission |
|---------|-------------|------------|
| `/telehop` | Show all commands (help) | Everyone |
| `/telehop version` | Show plugin version | Everyone |
| `/telehop reload` | Reload config, messages, and warp cache | `telehop.admin.reload` |
| `/telehop perms` | List all permission nodes with descriptions | `telehop.admin.permissions` |

The legacy staff parent grants the delegated administrative nodes.

Command examples use the default names. Set primary names and aliases in `config/commands.yml` and restart Paper. See [configuration](configuration.md#command-names-and-aliases).
