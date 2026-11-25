# TeleHop

TeleHop connects spawn, homes, warps, random teleportation and player teleport requests across Paper backends on a Velocity network. Players can use the same commands whether their destination is in another world on the same backend or on another server.

Name destinations in `spawns.yml` so members can visit them with `/spawn <name>`. A backend can have a different default for each loaded world, including Multiverse worlds. With the supplied configuration, bare `/spawn` still follows the existing regional or network hub route. Staff can delegate destination management and individual teleport actions through LuckPerms. [Spawn setup and examples](docs/spawns.md)

**3.0.1** adds the hub switch to the feature list and fills in missing settings on upgrades. [Enable the hub command](docs/spawns.md#enable-the-hub-command)

[Installation](#installation) · [Commands](#commands) · [Permissions](#permissions) · [Upgrade guide](LEGACY.md) · [Changelog](CHANGELOG.md) · [Contributing](CONTRIBUTING.md) · [Licence](LICENSE)

## Installation

| Where | Install in `plugins/` | Platform and Java |
|---|---|---|
| Each Paper backend | `telehop-paper-3.0.1.jar` | Paper 1.21, 1.21.1 and 1.21.3 through 1.21.11 on Java 21+, or 26.1.2 and 26.2 on Java 25+ |
| Each Velocity proxy | `telehop-velocity-3.0.1.jar` | Velocity 3.3 and 3.5 on Java 21, or 4.3.1 on Java 25 |

Velocity also supports Minecraft 1.21.2 clients connecting to a Paper 1.21.3 backend using their shared protocol. PaperMC did not publish a 1.21.2 server JAR.

Paper selects its Minecraft adapter at startup. Both adapters use Java 21 class files so older Paper servers can remap the entire JAR. The same Velocity JAR detects the proxy version and uses the messaging API shared by Velocity 3 and 4. Install the same **TeleHop release** across every proxy and backend.

1. Create a shared MySQL 8.0+ or MariaDB 10.5+ database and a user with permission to create and upgrade the schema. Start the instances once to generate their configuration.
2. On each proxy, configure the database and backend names in `plugins/telehop-velocity/config.properties`. On each Paper server, set the same database in `plugins/TeleHop/config/database.yml`.
3. In each backend's `config/general.yml`, set its unique `server-name` to its Velocity registration and set `hub-server` to the network hub. On a multi-proxy network, register the same backends on every proxy and give each proxy its own ID and the shared Redis connection.
4. Restart the proxies, then the backends. Join as a member and try `/spawn`, `/home` and the region and dimension menus in `/rtp`. Existing installations can follow the version-specific upgrade checks. [Setup](docs/setup.md) · [Multi-proxy configuration](docs/multi-proxy.md) · [Upgrading](LEGACY.md)

## Commands

These are the default names. Replace `<arguments>` with your values and omit `[optional arguments]` when you do not need them. To change a root command or its aliases, edit `config/commands.yml` and restart Paper. To change a destination name, edit `config/spawns.yml` on its owning backend and run `/telehop reload`. [Command configuration](docs/configuration.md#command-names-and-aliases)

### Spawn, homes and travel

| Command | Action | Permission |
|---|---|---|
| `/spawn` | Follow the source world's route, the backend default or the existing regional or network hub | `telehop.spawn` |
| `/spawn <name>` | Visit a configured destination | `telehop.spawn` and `telehop.spawn.named`, plus access to the destination |
| `/hub` | Visit the global hub's default spawn when `features.hub` is enabled in `config/features.yml` and Paper has restarted | `telehop.hub` |
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
| `/warps` | List global warps | `telehop.warp` |
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

## Permissions

These tables list every permission declared by the Paper plugin and the three permission patterns for named spawns, individual warps and personal warp limits. A permissions manager such as LuckPerms can grant or deny individual nodes.

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
| `telehop.spawn.destination.<name>` | Use the destination’s actual name. Public destinations allow travel by default. Restricted destinations require a grant. An explicit denial also blocks a public destination. |
| `telehop.warp.<name>` | Gives access to one global warp by name. The general `telehop.warp` permission already grants access to all global warps. To offer selected warps only, remove the general grant for that group and grant the named warps. Listing with `/warps` requires the general permission. |
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

The moderator in this example can inspect a home list. Grant `telehop.admin.homes.visit` as well for visits, or `telehop.admin.homes.delete` for deletion. [More LuckPerms examples](docs/permissions.md#luckperms-quick-setup)

## Configuration

- Paper uses split YAML files in `plugins/TeleHop/config/`. Set per-backend feature toggles in `features.yml`, RTP menus and region settings in `rtp.yml`, effects in `teleport.yml` and death behaviour in `respawn.yml`. The saved default spawn lives in `plugins/TeleHop/storage.yml`. Restart Paper after changing database settings, command names or optional hub command registration. [Configuration reference](docs/configuration.md)
- Player messages support English, Dutch, German, Spanish, Chinese and Polish. Edit the files in `plugins/TeleHop/languages/` and reload. [Messages and translations](docs/messages.md)
- MySQL stores player presence, requests, homes, logout locations and warps. It also stores the backend defaults, routes and named destinations published from Paper, including their worlds, coordinates and access settings. [SQL schema](sql/schema.sql) · [Spawn storage](docs/spawns.md#mysql-upgrades-and-backups)

## Building and releases

Configure Maven toolchains for JDK 21 and JDK 25, then run the full build from the repository root.

```sh
mvn clean verify
```

Installable outputs are `paper/target/telehop-paper-3.0.1.jar` and `velocity/target/telehop-velocity-3.0.1.jar`. CI checks the build, command and language resources, release version, changelog and runtime compatibility. The release workflow publishes the validated JARs and release notes to GitHub and Modrinth. [Release guide](docs/releases/publishing.md) · [Contributing](CONTRIBUTING.md)

## Further reading

[Legacy upgrade notes](LEGACY.md) · [Command guide](docs/commands.md) · [Network protocol](docs/protocol.md) · [Troubleshooting](docs/troubleshooting.md) · [Release history](CHANGELOG.md)

## Author and licence

Developed by **Epildev** at Epildevconnect Ltd, company number 17247566, registered in England and Wales. Discord handle `Epildev`. [GitHub](https://github.com/GitEpildev) · [Website](https://developer.epildevconnect.uk/) · [Companies House](https://find-and-update.company-information.service.gov.uk/company/17247566)

MIT License with Additional Terms. Attribution to Epildevconnect Ltd is required. [Licence text](LICENSE)
