# Permissions

Use this reference to grant member, moderator and administrator access on the Paper backends where players run TeleHop commands. The in-game reference is available through `/telehop perms` or `/telehop permissions` with `telehop.admin.permissions`. [Command syntax and aliases](commands.md)

These tables list every permission declared by the Paper plugin and the three permission patterns for named spawns, individual warps and personal warp limits. A permissions manager such as LuckPerms can grant or deny individual nodes.

## Member permissions

Granted to everyone by default.

| Permission | Purpose |
|---|---|
| `telehop.spawn` | Use bare `/spawn`. |
| `telehop.rtp` | Open the random teleport menu. |
| `telehop.warp` | List and visit global warps. |
| `telehop.pwarp` | Use personal warp commands. Creating one also requires an allowance. |
| `telehop.tpa` | Ask to visit a player. |
| `telehop.tpahere` | Invite a player to you. |
| `telehop.tpa.accept` | Accept a request. |
| `telehop.tpa.deny` | Decline a request. |
| `telehop.tpa.cancel` | Cancel your outgoing request. |
| `telehop.tpa.toggle` | Toggle incoming requests. |
| `telehop.homes` | Open the homes menu and travel to a saved home. |
| `telehop.sethome` | Save a home within your allowance. |
| `telehop.delhome` | Delete one of your own homes. |
| `telehop.homes.1` | Allow up to 1 home. |
| `telehop.lastlocation` | Return to your saved logout location. |
| `telehop.hub` | Use the optional `/hub` command when enabled. |
| `telehop.spawn.named` | Choose a named spawn with `/spawn <name>`. Requires `telehop.spawn` and destination access. |

## Operator permissions

Granted to server operators by default. Grant individual nodes to delegate only the actions a group needs.

| Permission | Purpose |
|---|---|
| `telehop.tp` | Grants all four player and coordinate teleport permissions listed below. |
| `telehop.tphere` | Bring another player to your location. |
| `telehop.admin` | Grants the management permissions listed below. |
| `telehop.back` | Return to your last pre-teleport location. |
| `telehop.back.death` | Return to your last death location. |
| `telehop.rtp.bypasscooldown` | Skip the RTP cooldown. |
| `telehop.rtp.bypassdelay` | Skip the RTP warmup. |
| `telehop.tpa.bypasscooldown` | Skip the outgoing TPA or TPA-here request cooldown. |
| `telehop.warps.unlimited` | Create unlimited personal warps. |

## Staff teleport actions

Each node defaults to false and is granted through `telehop.tp`. You can also grant each action directly.

| Permission | Purpose |
|---|---|
| `telehop.tp.player.self` | Visit another player. |
| `telehop.tp.player.others` | Move one player to another player. |
| `telehop.tp.coordinates.self` | Teleport yourself to coordinates. |
| `telehop.tp.coordinates.others` | Teleport another player to coordinates. |

## Staff management actions

Each node defaults to false and is granted through `telehop.admin`. You can also grant each action directly.

| Permission | Purpose |
|---|---|
| `telehop.admin.spawn.list` | List named destinations. |
| `telehop.admin.spawn.set` | Set named spawns and the ordinary backend spawn. |
| `telehop.admin.spawn.delete` | Delete a named spawn after confirmation. |
| `telehop.admin.homes.list` | Inspect another player’s homes. |
| `telehop.admin.homes.visit` | Visit another player’s listed home. |
| `telehop.admin.homes.set` | Set a home for another player. |
| `telehop.admin.homes.delete` | Delete a home for another player after confirmation. |
| `telehop.admin.lastlocation.view` | Inspect a saved logout location. |
| `telehop.admin.lastlocation.visit` | Visit a saved logout location. |
| `telehop.admin.lastlocation.clear` | Remove a saved logout location. |
| `telehop.admin.warps.set` | Create or update a global warp. |
| `telehop.admin.warps.list` | List personal warps across the network or by owner. |
| `telehop.admin.warps.delete` | Delete a global warp. |
| `telehop.admin.playerwarps.delete` | Delete another player’s personal warp. |
| `telehop.admin.playerinfo` | Inspect a player’s TeleHop data. |
| `telehop.admin.reload` | Reload supported Paper settings, spawn routes and messages. |
| `telehop.admin.permissions` | View the in-game permission reference. |

## Additional home slots

These nodes default to false. TeleHop uses the highest granted slot count up to `homes.max-slots`. Members receive `telehop.homes.1` by default.

| Permission | Purpose |
|---|---|
| `telehop.homes.2` | Allow up to 2 homes. |
| `telehop.homes.3` | Allow up to 3 homes. |
| `telehop.homes.4` | Allow up to 4 homes. |
| `telehop.homes.5` | Allow up to 5 homes. |
| `telehop.homes.6` | Allow up to 6 homes. |
| `telehop.homes.7` | Allow up to 7 homes. |
| `telehop.homes.8` | Allow up to 8 homes. |
| `telehop.homes.9` | Allow up to 9 homes. |
| `telehop.homes.10` | Allow up to 10 homes. |

## Permissions generated from your configuration

| Permission pattern | How it works |
|---|---|
| `telehop.spawn.destination.<name>` | Use the destination’s actual name. Public destinations allow travel by default. Restricted destinations require a grant. An explicit denial also blocks a public destination. |
| `telehop.warp.<name>` | Gives access to one global warp by name. The general `telehop.warp` permission already grants access to all global warps. To offer selected warps only, remove the general grant for that group and grant the named warps. Listing with `/warps` requires the general permission. |
| `telehop.warps.<number>` | Sets a personal warp allowance. Use a positive whole number such as `telehop.warps.3`. TeleHop takes the highest effective positive allowance. With no allowance a member cannot create personal warps. `telehop.warps.unlimited` removes the limit. |

## LuckPerms quick setup

Run the following examples from the server console. Replace the group and destination names with your own.

```bash
# Default rank: 3 player warps, 2 homes, last location
lp group default permission set telehop.warps.3
lp group default permission set telehop.homes.2
lp group default permission set telehop.lastlocation

# VIP rank: 10 player warps, 5 homes
lp group vip permission set telehop.warps.10
lp group vip permission set telehop.homes.5

# MVP rank: unlimited warps, 10 homes
lp group mvp permission set telehop.warps.unlimited
lp group mvp permission set telehop.homes.10

# Staff: all admin commands + back
lp group staff permission set telehop.admin
lp group staff permission set telehop.tp
lp group staff permission set telehop.tphere
lp group staff permission set telehop.back
lp group staff permission set telehop.back.death
lp group staff permission set telehop.warps.unlimited
lp group staff permission set telehop.homes.10
```

Both coordinate permissions are children of `telehop.tp`. Existing grants of the parent include both. If a group already inherits the parent, deny the specific operation you want to restrict. For example, to allow staff to teleport themselves to coordinates while blocking coordinate teleports of other players, run `lp group staff permission set telehop.tp.coordinates.others false`.

A moderator can inspect a home list with `lp group moderator permission set telehop.admin.homes.list true`. Grant `telehop.admin.homes.visit` separately for visits and `telehop.admin.homes.delete` for deletion. For a restricted destination named `staff-room`, use `lp group moderator permission set telehop.spawn.destination.staff-room true`. [Staff and destination examples](spawns.md#member-moderator-and-administrator-examples)
