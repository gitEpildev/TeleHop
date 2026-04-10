# Permissions

All permissions can be viewed in-game with `/telehop perms`.

## Default Permissions (everyone has these)

| Node | Description |
|------|-------------|
| `telehop.spawn` | Use `/spawn` |
| `telehop.rtp` | Use `/rtp` |
| `telehop.warp` | Use `/warp` and `/warps` |
| `telehop.pwarp` | Use `/pwarp` (player warps) |
| `telehop.tpa` | Use `/tpa` |
| `telehop.tpahere` | Use `/tpahere` |
| `telehop.tpa.accept` | Use `/tpaaccept` |
| `telehop.tpa.deny` | Use `/tpadeny` |
| `telehop.tpa.cancel` | Use `/tpacancel` |

## OP-Only / Admin Permissions

| Node | Description |
|------|-------------|
| `telehop.admin` | `/setwarp`, `/delwarp`, `/listwarps`, `/forcedelwarp`, `/telehop reload`, `/telehop perms`, `/pwarp admin del` |
| `telehop.tp` | Admin teleport (`/tp`) |
| `telehop.tphere` | Admin pull command (`/tphere`) |
| `telehop.rtp.bypasscooldown` | Skip the RTP cooldown |
| `telehop.rtp.bypassdelay` | Skip the RTP warmup delay |
| `telehop.tpa.bypasscooldown` | Skip the TPA cooldown |
| `telehop.warps.unlimited` | Unlimited player warps |

## Per-Warp Access

| Node | Description |
|------|-------------|
| `telehop.warp.<name>` | Access a specific admin warp (e.g. `telehop.warp.shop`) |

## Player Warp Limits

Control how many personal warps each player can create using `telehop.warps.<number>`:

| Node | Effect |
|------|--------|
| `telehop.warps.1` | Player can have 1 warp |
| `telehop.warps.3` | Player can have 3 warps |
| `telehop.warps.10` | Player can have 10 warps |
| `telehop.warps.100` | Player can have 100 warps |
| `telehop.warps.unlimited` | No limit |
| *(no permission)* | Cannot create warps (can still use admin warps) |

The plugin finds the **highest** matching `telehop.warps.<n>` value. If a player has both `telehop.warps.3` and `telehop.warps.10`, they get 10.

### LuckPerms Quick Setup

```bash
# Everyone can use basic commands (already default: true)

# Default rank: 3 player warps
lp group default permission set telehop.warps.3

# VIP rank: 10 player warps
lp group vip permission set telehop.warps.10

# Staff: unlimited warps + all admin commands
lp group staff permission set telehop.warps.unlimited
lp group staff permission set telehop.admin
lp group staff permission set telehop.tp
lp group staff permission set telehop.tphere
```
