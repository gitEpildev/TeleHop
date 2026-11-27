# Custom spawn destinations

On a fresh installation, `/spawn` follows the existing regional or network hub setting and uses the destination backend's saved spawn. Operators choose every additional destination name. The same Paper backend can host several Multiverse worlds, and one Velocity proxy can connect several Paper backends.

## Define your own names in YAML

On the Paper backend that owns the location, edit `plugins/TeleHop/config/<profile>/spawns.yml`, replacing `<profile>` with `single`, `multi` or `custom`. The following example defines two destinations. Replace the names, worlds and coordinates with values from your server.

```yaml
destinations:
  gardens:
    world: world
    x: 12.5
    y: 70
    z: -4.5
    yaw: 90
    pitch: 0
    access: public
  workshop:
    world: creative
    x: -8.5
    y: 68
    z: 21.5
    yaw: 0
    pitch: -12
    access: restricted

routing:
  enabled: false
  default: {}
  worlds: {}

```

Run `/telehop reload` on the backend you edited. Members can now run `/spawn gardens` from any participating backend. Grant `telehop.spawn.destination.workshop` to people who should have access to `/spawn workshop`. Changing a YAML key renames the destination on the next successful reload. Removing a key withdraws the destination. Update any routes and LuckPerms grants that use an old name. Tab suggestions refresh across backends within ten seconds. Travel reads the current database record.

Names are case-insensitive. Use one to 32 letters from the English alphabet, digits, underscores or hyphens. Start with a letter or digit. Quote all-digit YAML keys if you choose one. The shipped configuration defines no destinations.

## Change what bare `/spawn` does

Configure routes on the **source** Paper backend. In the example below, `/spawn` in the training world uses the gardens location owned by the survival backend. In other worlds players go to the hub backend's existing default spawn.

```yaml
routing:
  enabled: true
  default: {server: hub}
  worlds:
    training: {server: survival, spawn: gardens}
```

TeleHop checks the exact namespaced world key, then the world name, then the backend default. If none matches, it uses the existing regional or network hub. A route with only a server name selects that server's saved default directly. World names are local to their owning backend. A missing named destination or unloaded destination world returns an error. A local trip uses Paper directly. A remote trip uses the player's current Velocity connection and checks the destination again after arrival. For multiple proxies sharing these backends, see the [multi-proxy setup guide](multi-proxy.md).

Reload after editing routes.

## Enable the hub command

On each Paper backend where players should use the command, edit `plugins/TeleHop/config/<profile>/features.yml`. Set these entries under the existing `features` mapping.

```yaml
features:
  spawn: true
  hub: true
```

Restart the backend to register `/hub`. To change its label, enable `commands.custom-names.enabled` in the root `config.yml` and edit `commands/hub.yml`. For example, `hub: {custom-name: {enabled: true, name: lobby, aliases: [hub]}}` registers both labels. The command uses the global `hub-server` in the active `general.yml`. Enable spawn travel on the receiving backend. Players need `telehop.hub`, granted by default. If a command visibility plugin is installed, allow the labels you registered.

An upgrade from 3.0.0 inherits `hub-command.enabled` from the old `config/spawns.yml` when `features.hub` is absent. V4 removes the obsolete switch from the active spawn configuration. [Migration and recovery](migration.md)

## Manage destinations in game

| Command | Effect |
|---|---|
| `/telehop spawn set <name> public` | Save your current world, position and orientation to that named YAML entry. Use `restricted` for permission-based access. |
| `/telehop spawn setdefault` | Save the ordinary backend spawn to `storage.yml` and update its SQL record. |
| `/telehop spawn list` | Show the network directory, owners and access policies. |
| `/telehop spawn delete <name>` | Print a confirmation command bound to the current revision. |

If you have edited the YAML outside the game, reload it before using the management commands. Saving a location in game updates the same file as manual configuration.

## MySQL, upgrades and backups

Install the matching 4.0.0 backend and Velocity JARs together. Give each backend a unique `server-name` matching its Velocity registration. All instances must connect to the same database. Existing `storage.yml`, homes, warps and player data keep their existing formats. The backend creates a `spawns.yml` in each profile directory on first start.

| MySQL table | Stored data |
|---|---|
| `named_spawns` | Canonical name, owning backend, exact world, X/Y/Z, yaw, pitch, access policy and revision |
| `spawn_backends` | Backend name, default world, X/Y/Z, yaw, pitch, routing setting and hub command setting |
| `spawn_routes` | Owning backend, source world, target backend and optional target name. An empty source world identifies the backend default route. |

YAML is the source of configuration. At startup and reload, TeleHop validates it and replaces the owning backend's SQL snapshot in one transaction. A name already owned elsewhere rejects the whole change. An unchanged location retains its revision. A failed reload keeps the last accepted settings. Apply the correction in YAML and reload. Back up MySQL and every backend's TeleHop directory together.

## Member, moderator and administrator examples

```text
lp group moderator permission set telehop.spawn.destination.workshop true
lp group moderator permission set telehop.tp.player.self true
lp group moderator permission set telehop.admin.homes.list true
lp group admin permission set telehop.admin true
lp group admin permission set telehop.tp true
```

Members inherit `telehop.spawn`, `telehop.spawn.named` and `telehop.hub`. The moderator above can visit a player and inspect another player's home list. Grant `telehop.admin.homes.visit` to enable visits from that list. An explicit denial for a public destination takes precedence over its public setting. Permission checks use the player's effective Bukkit permissions, including the active LuckPerms context. If a command visibility plugin filters the command tree, allow the configured member commands there as well.
