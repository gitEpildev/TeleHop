# TeleHop

TeleHop is a cross-server teleportation suite for Paper and Velocity Minecraft networks. The Bukkit backend JAR also runs on standalone servers, Spigot, CraftBukkit and supported Paper forks. Players can use the same commands whether their destination is in another world on the same backend or on another server.

Name destinations in `spawns.yml` so members can visit them with `/spawn <name>`. A backend can have a different default for each loaded world, including Multiverse worlds. With the supplied configuration, bare `/spawn` still follows the existing regional or network hub route. Staff can delegate destination management and individual teleport actions through LuckPerms. [Spawn setup and examples](docs/spawns.md)

**4.1.0** adds ten-slot home pages, personal name suggestions for `/home` and `/homes`, and numbered or unlimited allowances. A 50-home rank has five pages, even before the player fills them. [Homes and permissions](docs/homes.md)

**4.0.0** adds deployment profiles, grouped command files and configurable RTP and spawn commands. The backend detects its platform at startup. [Platform matrix](docs/platforms.md) · [New configuration](docs/configuration.md) · [Upgrade from 3.0.1](docs/migration.md) · [V4 implementation and verification](docs/releases/4.0.0.md)

[Installation](#installation) · [Commands](#commands) · [Permissions](#permissions) · [Upgrade guide](LEGACY.md) · [Changelog](CHANGELOG.md) · [Contributing](CONTRIBUTING.md) · [Licence](LICENSE)

## Installation

Download the release for your installation and replace `<version>` below with its version number. Use the same TeleHop version across the network. [GitHub releases](https://github.com/gitEpildev/TeleHop/releases) · [Modrinth downloads](https://modrinth.com/plugin/telehop/versions)

| Where | Install in `plugins/` | Platform and Java |
|---|---|---|
| Each Paper backend | `telehop-bukkit-<version>.jar` | Paper 1.21, 1.21.1 and 1.21.3 through 1.21.11 on Java 21+, or 26.1.1, 26.1.2, 26.2 and 26.3 on Java 25+ |
| Each supported CraftBukkit, Spigot or Purpur backend | `telehop-bukkit-<version>.jar` | See the [exact implementation, Minecraft and Java combinations](docs/version-compatibility.md) |
| Each Velocity proxy | `telehop-velocity-<version>.jar` | Velocity 3.3 and 3.5 on Java 21, or 4.3.1 on Java 25 |

Two additional client versions passed a login and named teleport through Velocity. Minecraft 1.21.2 joined Paper 1.21.3 on Velocity 3.5, and Minecraft 26.1 joined Paper 26.1.2 on Velocity 4.3.1. Each pair shares a wire protocol.

The backend selects its Bukkit or native Paper operations and Minecraft adapter at startup. Both adapters use Java 21 class files so older Paper servers can remap the entire JAR. The same Velocity JAR detects the proxy version and uses the messaging API shared by Velocity 3 and 4. Install the same **TeleHop release** across every proxy and backend.

1. Create a shared MySQL 8.0+ or MariaDB 10.5+ database and a user with permission to create and upgrade the schema. Start the instances once to generate their configuration.
2. Select `SINGLE`, `MULTI` or `CUSTOM` in each instance's `config.yml`. Put the shared database settings in `config/database.yml`. The proxy also has a shared `config/redis.yml`.
3. In each backend's active `general.yml`, set its own `server-name` and `hub-server`. On Velocity, copy the registered names into the active `servers.yml`. Multi-proxy networks also need a unique proxy ID and a shared Redis channel.
4. Restart the proxies, then the backends. Join as a member and try `/spawn`, `/home` and the region and dimension menus in `/rtp`. Existing installations can follow the version-specific upgrade checks. [Setup](docs/setup.md) · [Multi-proxy configuration](docs/multi-proxy.md) · [Upgrading](LEGACY.md)

## Commands

These are the default names. Replace `<arguments>` with your values and omit `[optional arguments]` when you do not need them. To rename a command, enable `commands.custom-names.enabled` in the root `config.yml`. In its group under `commands/`, enable `custom-name.enabled`, set the name and aliases, then restart the backend. To change a destination name, edit `config/<profile>/spawns.yml` on its owning backend and run `/telehop reload`. [Command configuration](docs/configuration.md#command-names-and-aliases)

### Spawn, homes and travel

| Command | Action | Permission |
|---|---|---|
| `/spawn` | Follow the source world's route, the backend default or the existing regional or network hub | `telehop.spawn` |
| `/spawn <name>` | Visit a configured destination | `telehop.spawn` and `telehop.spawn.named`, plus access to the destination |
| `/hub` | Visit the global hub's default spawn when `features.hub` is enabled in `config/<profile>/features.yml` after the backend restarts | `telehop.hub` |
| `/rtp` | Choose a region and dimension, then travel to a safe random location | `telehop.rtp` |
| `/home`, `/homes`, `/home <name>`, `/homes <name>` | Open the paginated menu or tab-complete and visit one of your own homes | `telehop.homes` |
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
| `/telehop version`, `/telehop ver` | Display the backend plugin version | Everyone |
| `/telehop perms`, `/telehop permissions` | Display the permission reference | `telehop.admin.permissions` |
| `/telehop reload` | Reload backend settings, messages and spawn routes | `telehop.admin.reload` |
| `/telehop reload config` | Reload backend settings, messages and spawn routes | `telehop.admin.reload` |
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

## Permissions

These tables list every permission declared by the backend plugin and the configurable patterns for travel commands, RTP destinations, named spawns, individual warps and personal warp limits. A permissions manager such as LuckPerms can grant or deny individual nodes.

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
| `telehop.admin.reload` | Reload supported backend settings, spawn routes and messages. |
| `telehop.admin.permissions` | View the in-game permission reference. |

### Additional home slots

Members start with one home. Grant the highest allowance a rank needs, for example 20, 100 or 240. A lower allowance limits the menu to the new rank’s pages. Extra homes remain saved and accessible by name. An optional positive `homes.creation-cap` limits creation even for unlimited ranks.

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
| `telehop.homes.<number>` | Allow that many homes, including numbers above ten. |
| `telehop.homes.unlimited` | Allow unlimited homes, subject to any configured creation cap. |

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

The moderator in this example can inspect a home list. Grant `telehop.admin.homes.visit` as well for visits, or `telehop.admin.homes.delete` for deletion. [More LuckPerms examples](docs/permissions.md#luckperms-quick-setup)

## Configuration

Start with the [backend setup guide installed with the plugin](paper/src/main/resources/config/WIKI.md). The repository and backend JAR both contain the editable defaults under the same paths as the server folder. [Root config](paper/src/main/resources/config.yml) · [Single](paper/src/main/resources/config/single/) · [Multi](paper/src/main/resources/config/multi/) · [Custom](paper/src/main/resources/config/custom/)

The root `config.yml` selects **SINGLE** for a standalone or single-proxy network, **MULTI** for multiple proxies and Redis, or **CUSTOM** for individually selected settings. Throughout this guide, replace `<profile>` with the lowercase directory name. Start with the active profile’s `general.yml`, turn features on in `features.yml` and define destinations in `spawns.yml`. Use the global `commands/` folder for labels, aliases and additional travel variants. [Configuration reference](docs/configuration.md) · [RTP settings](docs/rtp.md)

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

- Player messages support English, Dutch, German, Spanish, Chinese and Polish. Edit the files in `plugins/TeleHop/languages/` and reload. [Messages and translations](docs/messages.md)
- MySQL stores player presence, requests, homes, logout locations and warps. It also stores the backend defaults, routes and named destinations published from Paper, including their worlds, coordinates and access settings. [SQL schema](sql/schema.sql) · [Spawn storage](docs/spawns.md#mysql-upgrades-and-backups)

## Building and releases

The backend starts in the `paper/` module, which produces the Bukkit JAR. The proxy starts in `velocity/`. Both use `common/` for storage, packet formats and services. [Source map and entry points](CONTRIBUTING.md#where-the-code-lives)

Configure Maven toolchains for JDK 21 and JDK 25, then run the full build from the repository root.

```sh
npm ci --prefix .github/release --ignore-scripts
mvn clean verify
bash .github/tests/bundle.sh
```

The packaging step writes `telehop-bukkit-<version>.jar`, `telehop-velocity-<version>.jar` and `SHA256SUMS` to `release-bundle/`. A successful CI run on `main` makes both JARs available as workflow artefacts. To publish them on GitHub Releases and Modrinth, a maintainer starts the Release workflow or pushes a version tag. [Release guide](docs/releases/publishing.md) · [Contributing](CONTRIBUTING.md)

## Further reading

[Legacy upgrade notes](LEGACY.md) · [Command guide](docs/commands.md) · [Network protocol](docs/protocol.md) · [Troubleshooting](docs/troubleshooting.md) · [Release history](CHANGELOG.md)

For help with your server, mention `@Epildev` on Discord. For a suspected security issue or a conduct concern, use the private contact instructions. [Support Discord](https://discord.gg/E8kpxdGbxn) · [Security reporting](SECURITY.md) · [Code of conduct](CODE_OF_CONDUCT.md)

## Author and licence

Developed by **Epildev** at Epildevconnect Ltd, company number 17247566, registered in England and Wales. Discord handle `Epildev`. [GitHub](https://github.com/GitEpildev) · [Website](https://developer.epildevconnect.uk/) · [Companies House](https://find-and-update.company-information.service.gov.uk/company/17247566)

MIT License with Additional Terms. Attribution to Epildevconnect Ltd is required. [Licence text](LICENSE)
