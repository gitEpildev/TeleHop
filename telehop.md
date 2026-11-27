# 🌐 TeleHop

**Cross-server teleportation suite for Paper and Velocity Minecraft networks.**

TeleHop supports **single-proxy and multi-proxy Velocity networks**. Use homes, warps, teleport requests, random teleportation and named spawns across your network. Players use the same commands when their destination is another Multiverse world on the same Paper server or a different backend behind Velocity.

[Downloads](https://modrinth.com/plugin/telehop/versions) · [GitHub](https://github.com/gitEpildev/TeleHop) · [Documentation](https://github.com/gitEpildev/TeleHop/tree/main/docs) · [Setup](https://github.com/gitEpildev/TeleHop/blob/main/docs/setup.md) · [Commands](https://github.com/gitEpildev/TeleHop/blob/main/docs/commands.md) · [Permissions](https://github.com/gitEpildev/TeleHop/blob/main/docs/permissions.md) · [Configuration](https://github.com/gitEpildev/TeleHop/blob/main/docs/configuration.md) · [Multi-proxy setup](https://github.com/gitEpildev/TeleHop/blob/main/docs/multi-proxy.md) · [Report an issue](https://github.com/gitEpildev/TeleHop/issues) · [Support Discord](https://discord.gg/E8kpxdGbxn)

## ✨ New in 4.0.0

One backend JAR detects compatible platforms, and one proxy JAR covers Velocity 3 and 4. Use the **SINGLE**, **MULTI** or **CUSTOM** deployment profile, rename command roots by category and define your own direct RTP, spawn and hub commands. Existing configuration migrates with a recovery history. Local and destination permissions are checked for additional travel commands. [Platforms and tested versions](https://github.com/gitEpildev/TeleHop/blob/main/docs/platforms.md) · [Configuration and upgrade guide](https://github.com/gitEpildev/TeleHop/blob/main/docs/migration.md)

## Named spawns in 3.0.0

Choose your own destinations in `spawns.yml`. Stand where players should arrive, face the direction they should look and run `/telehop spawn set market public`. Players can then use `/spawn market` from any participating backend. For a staff destination, use `/telehop spawn set staff-room restricted` and grant `telehop.spawn.destination.staff-room` to the appropriate group.

## 🧩 Single-proxy and multi-proxy networks

Paper places players at their destination. Velocity routes them between backends, and the shared database holds their saved locations.

- **One proxy and one Paper server:** Give each loaded Multiverse world its own spawn. Travel between worlds on that backend happens locally.
- **One proxy and several backends:** Send players to an exact world, position and facing direction on another registered server.
- **Several proxies and shared backends:** The traveller's own proxy handles the transfer even when other players on the backend entered through another proxy. A shared Redis service supports cross-proxy player tracking, player lists and request forwarding.

Bare `/spawn` follows the existing regional or network hub route by default. Override it per world or backend, or enable a separate `/hub` command for the global hub. Staff permissions for inspecting, visiting, setting and deleting homes can now be granted independently.

## 📍 Configure a world spawn

On a backend registered in Velocity as `survival`, the example below makes `/spawn` in the loaded `creative` world visit its gallery. The backend default sends other worlds to the saved spawn on `survival`. Members can type `/spawn gallery` directly.

```yaml
destinations:
  gallery:
    world: creative
    x: 12.5
    y: 70
    z: -4.5
    yaw: 90
    pitch: 0
    access: public

routing:
  enabled: true
  default: {server: survival}
  worlds:
    creative: {server: survival, spawn: gallery}

```

Save this in `plugins/TeleHop/config/<profile>/spawns.yml` and run `/telehop reload` on the owning backend. Replace the backend name, world and coordinates with your own. A valid reload publishes the updated locations and access rules to the shared database. Invalid edits keep the previously accepted settings active. [Spawn configuration and examples](https://github.com/gitEpildev/TeleHop/blob/main/docs/spawns.md)

## 🚀 Travel features

### Network spawn and random teleport

Use `/spawn` for the configured hub, or `/spawn <name>` for a named destination. Regional routing can take European players to `lobby-eu` and US players to `lobby-usa`. Choose the names and destinations for your own network.

Open `/rtp` to pick a region and dimension. The menu can show a live, colour-coded ping estimate. Selecting a destination starts the cooldown and safe-landing search. A pending trip prevents repeated clicks from stacking warmups. Enable the centred countdown and movement cancellation in your configuration.

### Teleport requests and return travel

Use `/tpa` to ask to visit someone or `/tpahere` to invite them to you. Recipients can click to accept or deny. Players can cancel an outgoing request or turn off incoming requests for the session. Server owners can configure timeouts, cooldowns, warmups and movement cancellation.

Use `/back` for the location before a teleport and `/back death` for the last death location. Both are tracked for the current session and can lead to another backend.

### 🏠 Named homes

Save a home with `/sethome <name>` or click an available bed in `/home`. A saved bed opens a menu to travel or confirm deletion. Locked beds show which slots are unavailable. Players can have up to ten homes according to their rank and the configured limit. Owners can customise the menu size, bed colours, server and world labels, coordinate visibility and the servers where new homes may be set. Players can visit existing homes from a backend that blocks setting new ones.

### 📍 Last logout location

TeleHop saves the player's backend, world, position and facing direction when they disconnect. Use `/lastlocation` to return there after logging in, even after a restart. Staff can inspect, visit or clear a saved logout location with the appropriate permissions.

### 🌍 Global and player warps

Staff can create network-wide warps such as `shop`, with individual access through `telehop.warp.shop`. Players can create private warps, make them public and visit another player's public warp. Rank permissions control how many personal warps each player can save.

### 💀 Random respawn

Enable random respawn per backend and choose the search radius in `config/<profile>/respawn.yml`. TeleHop searches the world where the player died by default. You can choose a fixed destination world, exclude worlds and honour beds and charged respawn anchors.

### 🛠️ Staff tools

Staff can inspect player records, manage homes and warps, publish spawn destinations and teleport across servers. Grant each action to the roles that need it. Players can check the in-game help and permission list, and staff suggestions follow the sender's permissions.

## 📋 Complete command reference

These are the default names. Replace `<arguments>` with your values and omit `[optional arguments]` when you do not need them. To change a root command or its aliases, edit the relevant file in `commands/` and restart Paper. To change a destination name, edit `config/<profile>/spawns.yml` on its owning backend and run `/telehop reload`. [Command configuration](https://github.com/gitEpildev/TeleHop/blob/main/docs/configuration.md#command-names-and-aliases)

### Spawn, homes and travel

| Command | Action | Permission |
|---|---|---|
| `/spawn` | Follow the source world's route, the backend default or the existing regional or network hub | `telehop.spawn` |
| `/spawn <name>` | Visit a configured destination | `telehop.spawn` and `telehop.spawn.named`, plus access to the destination |
| `/hub` | Visit the global hub's default spawn when `features.hub` is enabled in `config/<profile>/features.yml` and Paper has restarted | `telehop.hub` |
| `/rtp` | Choose a region and dimension, then travel to a safe random location | `telehop.rtp` |
| `/home`, `/home <name>` | Open the homes menu or travel directly to a named home | `telehop.homes` |
| `/sethome <name>` | Save a home at your current location | `telehop.sethome` |
| `/delhome <name>` | Delete one of your homes | `telehop.delhome` |
| `/lastlocation` (`/lastloc`, `/backlast`, `/ll`) | Return to your saved logout location | `telehop.lastlocation` |
| `/back` | Return to your previous teleport location | `telehop.back` |
| `/back death` | Return to your last death location | `telehop.back.death` |

Logout locations persist in the database. Back and death locations last for the session. Both back permissions default to operators. A restricted spawn requires `telehop.spawn.destination.<name>`. An explicit denial of that node also blocks access to a public destination.

### Teleport requests

| Command | Action | Permission |
|---|---|---|
| `/tpa <player>` | Ask to visit the player | `telehop.tpa` |
| `/tpahere <player>` | Ask the player to visit you | `telehop.tpahere` |
| `/tpaaccept` | Accept an incoming request | `telehop.tpa.accept` |
| `/tpadeny` | Decline an incoming request | `telehop.tpa.deny` |
| `/tpacancel` | Withdraw your outgoing request | `telehop.tpa.cancel` |
| `/tpatoggle` | Switch incoming requests on or off | `telehop.tpa.toggle` |

### Global and player warps

The default aliases for `/pwarp` are `/playerwarp` and `/pwarps`. Both accept the same arguments.

| Command | Action | Permission |
|---|---|---|
| `/warps` | List accessible global warps | `telehop.warp` or an individual warp grant |
| `/warp <name>` | Visit a global warp | `telehop.warp` or `telehop.warp.<name>` |
| `/setwarp <name>` | Create or update a global warp | `telehop.admin.warps.set` |
| `/delwarp <name>` | Delete a global warp | `telehop.admin.warps.delete` |
| `/pwarp`, `/pwarp list` | List your personal warps and your allowance | `telehop.pwarp` |
| `/pwarp <name>` | Visit your own warp | `telehop.pwarp` |
| `/pwarp <player> <name>` | Visit another player's public warp | `telehop.pwarp` |
| `/pwarp set <name>` | Create a personal warp. Also accepts `create` | `telehop.pwarp` and an available warp slot |
| `/pwarp del <name>` | Remove a personal warp. Also accepts `delete` and `remove` | `telehop.pwarp` |
| `/pwarp public <name>` | Switch a warp between public and private. Also accepts `toggle` | `telehop.pwarp` |
| `/pwarp admin del <player> <name>` | Remove a player's warp. Also accepts `admin delete` and `admin remove` | `telehop.admin.playerwarps.delete` |

### Staff teleports

| Command | Action | Permission |
|---|---|---|
| `/tp <player>` | Visit a player | `telehop.tp.player.self` |
| `/tp <player> <target>` | Move the first player to the second | `telehop.tp.player.others` |
| `/tp <x> <y> <z>` | Move to coordinates in your world | `telehop.tp.coordinates.self` |
| `/tp <player> <x> <y> <z>` | Move the named player to coordinates | `telehop.tp.coordinates.others` for another player, `telehop.tp.coordinates.self` for yourself |
| `/tphere <player>` | Bring the player to you | `telehop.tphere` |

### Management and help

| Command | Action | Permission |
|---|---|---|
| `/telehop`, `/telehop help` | Display command help | Everyone |
| `/telehop version`, `/telehop ver` | Display the Paper plugin version | Everyone |
| `/telehop perms`, `/telehop permissions` | Display the permission reference | `telehop.admin.permissions` |
| `/telehop reload` | Reload Paper settings, messages and spawn routes | `telehop.admin.reload` |
| `/telehop reload config` | Reload Paper settings, messages and spawn routes | `telehop.admin.reload` |
| `/telehop reload messages` | Reload only the active language and its English fallback | `telehop.admin.reload` |
| `/telehop commands list` | List the registered command labels | `telehop.admin.commands.list` |
| `/telehop commands validate` | Validate grouped command files | `telehop.admin.commands.validate` |
| `/telehop reload commands` | Apply validated command settings and report registration changes that need a restart | `telehop.admin.commands.reload` |
| `/telehop config validate` | Check the active configuration without applying it | `telehop.admin.config.validate` |
| `/telehop platform info` | Show the detected server implementation, version and capabilities | `telehop.admin.platform.info` |
| `/telehop migration status` | Show the configuration schema, deployment mode and migration identifier | `telehop.admin.migration.status` |
| `/telehop diagnostics` | Show the deployment mode, backend identity and database circuit state | `telehop.admin.diagnostics` |
| `/telehop spawn list` | Show the network's named destinations | `telehop.admin.spawn.list` |
| `/telehop spawn set <name> <access>` | Save your position on this backend with access set to `public` or `restricted` | `telehop.admin.spawn.set` |
| `/telehop spawn setdefault` | Save the ordinary backend spawn at your position | `telehop.admin.spawn.set` |
| `/telehop spawn delete <name> [revision]` | Request deletion on the owning backend, then confirm with the displayed revision | `telehop.admin.spawn.delete` |
| `/listhomes <player>` | Inspect a player's homes | `telehop.admin.homes.list` |
| `/listhomes visit <owner-uuid> <name>` | Visit a listed home | `telehop.admin.homes.visit` |
| `/forcesethome <player> <name>` | Save a home for another player at your position | `telehop.admin.homes.set` |
| `/forcedelhome <player>` | List the player's homes with deletion buttons | `telehop.admin.homes.delete` |
| `/forcedelhome confirm <owner-uuid> <name> [confirm]`, `/forcedelhome-confirm <owner-uuid> <name> [confirm]` | Show the deletion prompt. The final `confirm` argument deletes the home | `telehop.admin.homes.delete` |
| `/forcelastloc <player>` | Inspect a saved logout location | `telehop.admin.lastlocation.view` |
| `/forcelastloc tp <player>` | Visit the saved logout location | `telehop.admin.lastlocation.visit` |
| `/forcelastloc clear <player>` | Remove the saved logout location | `telehop.admin.lastlocation.clear` |
| `/playerinfo <player>` (`/pinfo`) | Inspect a player's TeleHop data | `telehop.admin.playerinfo` |
| `/listwarps [player]` | List personal warps across the network or for one owner | `telehop.admin.warps.list` |
| `/forcedelwarp <name>` | Delete a global warp | `telehop.admin.warps.delete` |
| `/forcedelwarp <player> <name>` | Delete a player's personal warp | `telehop.admin.playerwarps.delete` |

`/forcell` is an alias for `/forcelastloc`, including its `tp` and `clear` subcommands. Home lists only show staff actions the viewer has permission to perform.

## 🔐 Complete permission reference

The command tables above show which action each permission unlocks. The tables below give the default for every permission declared in the Paper JAR. A permissions manager such as LuckPerms can grant or deny individual nodes.

### Member permissions

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
| `telehop.hub` | Use the global hub command when `features.hub` is enabled on this backend. |
| `telehop.spawn.named` | Choose a named spawn with `/spawn <name>`. Requires `telehop.spawn` and destination access. |

### Operator permissions

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

### Staff teleport actions

Each node defaults to false and is granted through `telehop.tp`. You can also grant each action directly.

| Permission | Purpose |
|---|---|
| `telehop.tp.player.self` | Visit another player. |
| `telehop.tp.player.others` | Move one player to another player. |
| `telehop.tp.coordinates.self` | Teleport yourself to coordinates. |
| `telehop.tp.coordinates.others` | Teleport another player to coordinates. |

### Staff management actions

Each node defaults to false and is granted through `telehop.admin`. You can also grant each action directly.

| Permission | Purpose |
|---|---|
| `telehop.admin.commands.list` | List the registered command labels. |
| `telehop.admin.commands.validate` | Validate grouped command files. |
| `telehop.admin.commands.reload` | Apply validated command settings and report registration changes that need a restart. |
| `telehop.admin.config.validate` | Check the active configuration without applying it. |
| `telehop.admin.platform.info` | Show the detected server implementation, version and capabilities. |
| `telehop.admin.migration.status` | Show the configuration schema, deployment mode and migration identifier. |
| `telehop.admin.diagnostics` | Show the deployment mode, backend identity and database circuit state. |
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

### Additional home slots

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

### Permissions generated from your configuration

| Permission pattern | How it works |
|---|---|
| `telehop.command.<id>` | Use an additional command when its configured permission check is enabled. The ID stays the same when its label changes. |
| `telehop.rtp.region.<id>` | Use a configured RTP region when the variant requires this permission. |
| `telehop.rtp.server.<id>` | Use a configured RTP server when the variant requires this permission. |
| `telehop.rtp.world.<id>` | Use a configured RTP world when the variant requires this permission. |
| `telehop.rtp.dimension.<id>` | Use a configured RTP dimension when the variant requires this permission. |
| `telehop.spawn.destination.<name>` | Use the destination’s actual name. Public destinations allow travel by default. Restricted destinations require a grant. An explicit denial also blocks a public destination. |
| `telehop.warp.<name>` | Grants one global warp. An explicit denial overrides general travel access. With only individual grants, `/warps` lists the authorised existing warps. |
| `telehop.warps.<number>` | Sets a personal warp allowance. Use a positive whole number such as `telehop.warps.3`. TeleHop takes the highest effective positive allowance. With no allowance a member cannot create personal warps. `telehop.warps.unlimited` removes the limit. |

### LuckPerms console examples

```text
lp group default permission set telehop.homes.2 true
lp group default permission set telehop.warps.3 true
lp group moderator permission set telehop.tp.player.self true
lp group moderator permission set telehop.admin.homes.list true
lp group moderator permission set telehop.spawn.destination.staff-room true
lp group admin permission set telehop.admin true
lp group admin permission set telehop.tp true
lp group admin permission set telehop.tphere true
```

The moderator in this example can inspect a home list. Grant `telehop.admin.homes.visit` as well for visits, or `telehop.admin.homes.delete` for deletion. [Permission guide](https://github.com/gitEpildev/TeleHop/blob/main/docs/permissions.md)

## 📥 Requirements and compatibility

| Install location | TeleHop 4.0.0 file | Supported platform |
|---|---|---|
| Each Paper backend’s `plugins/` folder | `telehop-paper-4.0.0.jar` | Paper 1.21, 1.21.1 and 1.21.3 through 1.21.11 on Java 21, or Paper 26.1.1, 26.1.2, 26.2 and 26.3 on Java 25 |
| Each supported CraftBukkit, Spigot or Purpur backend’s `plugins/` folder | `telehop-paper-4.0.0.jar` | See the [tested backend versions](https://github.com/gitEpildev/TeleHop/blob/main/docs/version-compatibility.md) |
| Each Velocity proxy’s `plugins/` folder | `telehop-velocity-4.0.0.jar` | Velocity 3.3 and 3.5 on Java 21, or Velocity 4.3.1 on Java 25 |

Minecraft 1.21.2 clients passed a login and named teleport through Velocity 3.5 to Paper 1.21.3. Minecraft 26.1 clients passed the same checks through Velocity 4.3.1 to Paper 26.1.2. TeleHop 4.0.0 also passed two-player network tests with Multiverse-Core 5.8.2 and LuckPerms 5.5.71.

Standalone Bukkit-compatible backends can run local travel, including named destinations in loaded Multiverse worlds. Add Velocity for cross-server travel and Redis for cross-proxy synchronisation. LuckPerms is optional. The backend selects its platform adapter automatically. See the [tested server implementations and versions](https://github.com/gitEpildev/TeleHop/blob/main/docs/version-compatibility.md).

## ⚙️ Setup

1. Install matching TeleHop JARs on every proxy and backend. Start the instances once to generate their configuration.
2. Connect all instances to the same MySQL 8.0+ or MariaDB 10.5+ database. TeleHop creates and upgrades its database tables on startup.
3. Give each Paper backend a unique `server-name` matching its Velocity registration. Set `hub-server` to the backend that owns the global hub.
4. For a multi-proxy network, register the same backends on every proxy, give each proxy a unique `proxy.id` in its active profile and configure the shared Redis service. Redis supports TLS, certificate and hostname verification and a custom CA certificate.
5. Restart the proxies and backends, then test a home, a named spawn and a regional RTP destination as a member.

## 📁 Configuration layout

Replace `<profile>` in file paths with `single`, `multi` or `custom`. The backend reads routes, features, menus and timings from that profile. Command labels live in `commands/`, translations live in `languages/`, and credentials stay in shared files under `config/`. Velocity also reads YAML. Existing properties migrate during the first upgrade.

```text
plugins/TeleHop/
  config.yml
  config/
    database.yml
    WIKI.md
    single/   multi/   custom/
  commands/
    spawn.yml   hub.yml   teleport.yml   tpa.yml   rtp.yml   back.yml
    homes.yml   warps.yml   player-warps.yml   lastlocation.yml   playerinfo.yml   admin.yml
  languages/
    en.yml   nl.yml   de.yml   es.yml   zh.yml   pl.yml
  migrations/
    state.yml   history.log   backups/
  storage.yml

plugins/telehop-velocity/
  config.yml
  config/
    database.yml   redis.yml
    single/   multi/   custom/
  migrations/
    state.yml   history.log   backups/
```

In the backend SINGLE profile, `general.yml` also contains the messaging and region settings. MULTI and CUSTOM give these sections their own `messaging.yml` and `regions.yml` files.

## 🌐 Velocity configuration

Select the deployment mode in `plugins/telehop-velocity/config.yml`. Each proxy reads the following files when it starts.

| File | Purpose |
|---|---|
| `config/database.yml` | Shared MySQL or MariaDB settings and connection pool size |
| `config/redis.yml` | Redis host, port, credentials, channel prefix, TLS verification and optional CA certificate |
| `config/<profile>/servers.yml` | Registered backend names and the hub. In SINGLE, this also holds the `proxy` section. |
| `config/multi/proxy.yml` or `config/custom/proxy.yml` | Unique proxy ID, global player list, cross-proxy timeout and CUSTOM deployment options |
| `config/<profile>/messaging.yml` | Request timeout and duplicate-message window |

Register the same backend names in Velocity’s `velocity.toml`. The first V4 start imports an existing `config.properties`. [Velocity configuration](https://github.com/gitEpildev/TeleHop/blob/main/docs/velocity.md)

## 🗄️ Database tables

TeleHop creates and upgrades its tables on startup. Manage named spawns through the owning backend's YAML or in-game commands.

| Table | What TeleHop stores |
|---|---|
| `players` | Player identity and current backend |
| `homes` | Each player's named homes and locations |
| `warps` | Global warp locations |
| `player_warps` | Personal warp locations, owners and visibility |
| `tpa_requests` | Teleport requests between players |
| `last_locations` | Each player's saved logout location |
| `spawn_backends` | Each backend's ordinary spawn and routing settings |
| `spawn_routes` | Destination backend and optional named spawn for a source route |
| `named_spawns` | Destination name, owning backend, world, coordinates, facing direction, access and revision |

## 💬 Languages

TeleHop includes English, Dutch, German, Spanish, Simplified Chinese and Polish translations. Messages support MiniMessage formatting. Edit the files in `plugins/TeleHop/languages/` and reload. Custom values take priority. Missing keys fall back to the selected bundled translation, then to English.

## 🔄 Updating an existing network

Back up the shared database, both installed JARs and each instance's TeleHop folder. Install matching 4.0.0 builds across the network. Follow the checks for your installed version and each subsequent upgrade. [Full upgrade and recovery guide](https://github.com/gitEpildev/TeleHop/blob/main/LEGACY.md) · [Changelog](https://github.com/gitEpildev/TeleHop/blob/main/CHANGELOG.md)

| Upgrading from | What to check |
|---|---|
| 1.x, including 1.0.0 and 1.0.2 | Keep the old `config.yml` for migration to split YAML. A numbered home such as slot 1 becomes `Home 1`. Verify an existing player's home count and destination after upgrading. |
| 2.0.x | Compare your RTP menu settings with `rtp.gui.ping` and check destination selection and cooldowns. |
| 2.1.x | Review `show-countdown`, your warmup duration and any custom `countdown-title` translation. |
| 2.2.x | Review the Paper command names and aliases in the matching file under `commands/`, including any command visibility rules. |
| 2.3.x | Keep your saved spawn, homes, warps and aliases. Test ordinary `/spawn` through each entry proxy before enabling custom routes. Then test a named destination locally and from another backend. |
| 3.0.0 | Paper imports the previous hub command setting into `features.hub`, fills in missing feature and command entries and retains custom aliases. Restart Paper after changing the switch. |
| 3.0.1 | Stop the instance, install the V4 JAR and start it once. Confirm the profile, grouped command files, database settings and saved destinations. Follow the migration log for recovery. |

## 🧪 Building from source

Use Java 21 for Maven and configure the Java 25 toolchain for the newer Paper adapter. Run the following from the repository root.

```sh
mvn clean verify
```

Installable outputs are `paper/target/telehop-paper-4.0.0.jar` and `velocity/target/telehop-velocity-4.0.0.jar`. [Source code](https://github.com/gitEpildev/TeleHop) · [Contributing](https://github.com/gitEpildev/TeleHop/blob/main/CONTRIBUTING.md)

## 👤 Author and company

Developed by **Epildev** at **Epildevconnect Ltd**, registered in England and Wales under company number **17247566**. [Company website](https://developer.epildevconnect.uk/) · [Companies House registration](https://find-and-update.company-information.service.gov.uk/company/17247566)

## 🔗 Socials and support

| Where to find me | Contact |
|---|---|
| Personal GitHub | [GitEpildev](https://github.com/GitEpildev) |
| Discord | Join the [GitEpildev Support Server](https://discord.gg/E8kpxdGbxn) and mention **@Epildev** for help. |
| Developer website | [developer.epildevconnect.uk](https://developer.epildevconnect.uk/) |
| TeleHop on GitHub | [Repository](https://github.com/gitEpildev/TeleHop) · [Report an issue](https://github.com/gitEpildev/TeleHop/issues) |
| TeleHop on Modrinth | [Project page](https://modrinth.com/plugin/telehop) · [Version downloads](https://modrinth.com/plugin/telehop/versions) |

When reporting a problem, include your TeleHop, Paper, Velocity and Java versions, the command you ran and whether the route crossed a world, backend or proxy.

## 📄 Licence

TeleHop is distributed under the **MIT License with Additional Terms**. Public use, distribution and derivative works require attribution to **Epildevconnect Ltd**. [Read the full licence](https://github.com/gitEpildev/TeleHop/blob/main/LICENSE)
