# Commands

## Spawn

| Command | Description | Permission |
|---------|-------------|------------|
| `/spawn` | Teleport to the network spawn (hub server) | `telehop.spawn` |

## Admin Warps

| Command | Description | Permission |
|---------|-------------|------------|
| `/warp <name>` | Teleport to a global warp | `telehop.warp` |
| `/setwarp <name>` | Create or update a global warp | `telehop.admin` |
| `/delwarp <name>` | Delete a global warp | `telehop.admin` |
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
| `/pwarp admin del <player> <name>` | Admin: delete any player's warp | `telehop.admin` |

Aliases: `/playerwarp`, `/pwarps`

## TPA (Teleport Ask)

| Command | Description | Permission |
|---------|-------------|------------|
| `/tpa <player>` | Ask to teleport TO another player | `telehop.tpa` |
| `/tpahere <player>` | Ask another player to teleport to YOU | `telehop.tpahere` |
| `/tpaaccept` | Accept an incoming request | `telehop.tpa.accept` |
| `/tpadeny` | Deny an incoming request | `telehop.tpa.deny` |
| `/tpacancel` | Cancel your outgoing request | `telehop.tpa.cancel` |

## RTP (Random Teleport)

| Command | Description | Permission |
|---------|-------------|------------|
| `/rtp` | Random teleport — opens region/dimension GUI | `telehop.rtp` |

## Admin Teleport

| Command | Description | Permission |
|---------|-------------|------------|
| `/tp <player>` | Teleport to a player (cross-server) | `telehop.tp` |
| `/tp <p1> <p2>` | Teleport player p1 to player p2 | `telehop.tp` |
| `/tphere <player>` | Pull a player to your location | `telehop.tphere` |

## Admin Warp Management

| Command | Description | Permission |
|---------|-------------|------------|
| `/listwarps` | List all player warps across all servers | `telehop.admin` |
| `/listwarps <player>` | List a specific player's warps with details | `telehop.admin` |
| `/forcedelwarp <name>` | Force-delete an admin warp | `telehop.admin` |
| `/forcedelwarp <player> <name>` | Force-delete a specific player's warp | `telehop.admin` |

## TeleHop Admin

| Command | Description | Permission |
|---------|-------------|------------|
| `/telehop` | Show all commands (help) | Everyone |
| `/telehop reload` | Reload config, messages, and warp cache | `telehop.admin` |
| `/telehop version` | Show plugin version | Everyone |
| `/telehop perms` | List all permission nodes with descriptions | `telehop.admin` |
