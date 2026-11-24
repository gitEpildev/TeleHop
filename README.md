# TeleHop

TeleHop connects spawn, homes, warps, random teleportation and player teleport requests across Paper backends on a Velocity network. Players can use the same commands whether their destination is in another world on the same backend or on another server.

**TeleHop 3.0.0** lets you give destinations your own names in `spawns.yml` and let members use `/spawn <name>`. A backend can have a different default for each loaded world, including Multiverse worlds. With the supplied configuration, bare `/spawn` still follows the existing regional or network hub route. Staff can delegate destination management and individual teleport actions through LuckPerms. [Spawn setup and examples](docs/spawns.md)

[Installation](#installation) · [Commands](#commands) · [Upgrade guide](LEGACY.md) · [Changelog](CHANGELOG.md) · [Contributing](CONTRIBUTING.md) · [Licence](LICENSE)

## Installation

| Where | Install in `plugins/` | Platform and Java |
|---|---|---|
| Each Paper backend | `telehop-paper-3.0.0.jar` | Paper 1.21, 1.21.1 and 1.21.3 through 1.21.11 on Java 21+, or 26.1.2 and 26.2 on Java 25+ |
| Each Velocity proxy | `telehop-velocity-3.0.0.jar` | Velocity 3.3 and 3.5 on Java 21, or 4.3.1 on Java 25 |

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
| `/hub` | Visit the global hub's default spawn when enabled in `spawns.yml` | `telehop.hub` |
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

## Permissions and configuration

- Grant each staff group the individual nodes it needs. `telehop.admin` grants the management commands. `telehop.tp` grants all four `/tp` forms. `telehop.tphere` controls the pull command separately. [Permission defaults](docs/permissions.md) · [LuckPerms examples](docs/spawns.md#member-moderator-and-administrator-examples)
- Grant `telehop.homes.1` through `telehop.homes.10` for home slots. Grant a positive limit such as `telehop.warps.3` or use `telehop.warps.unlimited` for personal warps. [Homes](docs/homes.md) · [Warps](docs/warps.md)
- Paper uses split YAML files in `plugins/TeleHop/config/`. Set per-backend feature toggles in `features.yml`, RTP menus and region settings in `rtp.yml`, effects in `teleport.yml` and death behaviour in `respawn.yml`. The saved default spawn lives in `plugins/TeleHop/storage.yml`. Restart Paper after changing database settings, command names or optional hub command registration. [Configuration reference](docs/configuration.md)
- Player messages support English, Dutch, German, Spanish, Chinese and Polish. Edit the files in `plugins/TeleHop/languages/` and reload. [Messages and translations](docs/messages.md)
- MySQL stores player presence, requests, homes, logout locations and warps. It also stores the backend defaults, routes and named destinations published from Paper, including their worlds, coordinates and access settings. [SQL schema](sql/schema.sql) · [Spawn storage](docs/spawns.md#mysql-upgrades-and-backups)

## Building and releases

Configure Maven toolchains for JDK 21 and JDK 25, then run the full build from the repository root.

```sh
mvn clean verify
```

Installable outputs are `paper/target/telehop-paper-3.0.0.jar` and `velocity/target/telehop-velocity-3.0.0.jar`. CI checks the build, command and language resources, release version, changelog and runtime compatibility. The release workflow publishes the validated JARs and release notes to GitHub and Modrinth. [Release guide](docs/releases/publishing.md) · [Contributing](CONTRIBUTING.md)

## Further reading

[Legacy upgrade notes](LEGACY.md) · [Command guide](docs/commands.md) · [Network protocol](docs/protocol.md) · [Troubleshooting](docs/troubleshooting.md) · [Release history](CHANGELOG.md)

## Author and licence

Developed by **Epildev** at Epildevconnect Ltd, company number 17247566, registered in England and Wales. Discord handle `Epildev`. [GitHub](https://github.com/GitEpildev) · [Website](https://developer.epildevconnect.uk/) · [Companies House](https://find-and-update.company-information.service.gov.uk/company/17247566)

MIT License with Additional Terms. Attribution to Epildevconnect Ltd is required. [Licence text](LICENSE)
