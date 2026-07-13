# 🌐 TeleHop

Cross-server teleportation suite for Paper and Velocity Minecraft networks.

TeleHop is built for multi-server Minecraft networks. It connects Paper backend servers through Velocity and keeps teleport data synced across the full network.

Players can use homes, warps, player warps, TPA, RTP, `/back`, `/spawn`, and last-location teleporting across servers.

TeleHop stores shared data in MySQL, so homes, warps, player warps, TPA requests, player tracking, and logout locations stay synced across every server.

[GitHub](https://github.com/gitEpildev/TeleHop) · [Documentation](https://github.com/gitEpildev/TeleHop/tree/main/docs) · [Setup](https://github.com/gitEpildev/TeleHop/blob/main/docs/setup.md) · [Commands](https://github.com/gitEpildev/TeleHop/blob/main/docs/commands.md) · [Permissions](https://github.com/gitEpildev/TeleHop/blob/main/docs/permissions.md) · [Configuration](https://github.com/gitEpildev/TeleHop/blob/main/docs/configuration.md) · [Support](https://github.com/gitEpildev/TeleHop/issues)

---

## ✨ What TeleHop Does

TeleHop gives your Velocity network a complete teleport system.

* Cross-server `/spawn`
* Cross-server TPA
* Cross-server homes
* Cross-server warps
* Cross-server player warps
* Cross-server `/back`
* Cross-server admin teleport
* Named homes
* Last logout location teleporting
* Random teleport GUI
* Random respawn system
* MySQL-backed storage
* Optional Redis TLS support for multi-proxy networks
* Modular config files
* Multi-language support
* LuckPerms support
* Permission-based limits
* Admin management tools

---

## 📦 Version 2.1.0

TeleHop 2.1.0 adds encrypted Redis connections, live destination-ping display in the RTP menu, and fixes the RTP selection and cooldown flow.

### Added

* Redis TLS support through `redis.ssl`, `redis.ssl-verify`, and `redis.ssl-ca-cert`.
* Certificate and hostname verification for Redis connections, with support for a custom PEM CA certificate.
* Destination-ping display on RTP region items.
* Colour-coded ping values: green below 80 ms, yellow below 150 ms, and red above.
* Live ping refresh twice per second while the RTP region menu is open.
* Proxy-to-backend RTT measurement and the `SERVER_PING_UPDATE` network packet.
* `rtp.gui.ping.enabled` and `rtp.gui.ping.proxy-ping` configuration options.

### Fixed

* RTP menus close immediately after a destination is selected.
* RTP cooldown starts only after a player selects a destination.
* Pending RTP protection prevents players from stacking warmups through repeated clicks or `/rtp` commands.
* RTP GUI tooltips hide standard item information, leaving the configured name, description, and ping line.

### Migration

* Update the Paper and Velocity JARs together, as 2.0.0 does not recognise the new `SERVER_PING_UPDATE` packet.
* Add the `rtp.gui.ping` section to existing `rtp.yml` files, or regenerate the file from the default configuration.
* Add `rtp-in-progress` to customised language files. English fallback applies if the key is absent.
* No database changes are required.

---

## 🚀 Teleportation

### Network Spawn

`/spawn` sends players to the configured hub server.

Spawn supports region-aware routing for multi-proxy networks.

Example:

* EU players can route to `lobby-eu`
* US players can route to `lobby-usa`

### Random Teleport

`/rtp` opens a GUI where players choose a region and dimension.

TeleHop then finds a safe landing spot inside the configured radius.

RTP supports:

* Region selection
* Dimension selection
* Destination-ping display with a live, colour-coded value
* Cooldown applied after destination selection
* Warmup with pending-RTP protection
* Move cancellation
* Async safe-location search
* Safe landing checks
* GUI back button

### TPA

TeleHop supports cross-server teleport requests.

Commands:

* `/tpa <player>`
* `/tpahere <player>`
* `/tpaaccept`
* `/tpadeny`
* `/tpacancel`
* `/tpatoggle`

TPA supports:

* Cross-server requests
* Clickable accept and deny buttons
* Timeout
* Cooldown
* Warmup
* Move cancellation
* Session-based TPA toggle
* Sender notification when the target has requests disabled

### Back

Commands:

* `/back`
* `/back death`

Features:

* Return to your last location before teleporting
* Return to your last death location
* Works across servers
* Session-based location tracking

---

## 🏠 Named Homes

TeleHop now uses named homes instead of numeric slots.

Players can create, delete, and teleport to homes by name.

### Commands

* `/home`
* `/home <name>`
* `/sethome <name>`
* `/delhome <name>`

### Features

* Up to 10 homes per player
* Permission-based home limits
* Cross-server home teleportation
* Case-insensitive home names
* Names support letters, numbers, and spaces
* Maximum home name length of 32 characters
* 5-row homes GUI
* Configurable bed colours
* Empty beds can set homes
* Occupied beds teleport to homes
* Shift-click can delete homes
* Locked slots show upgrade messaging
* Server and world labels in the GUI
* Blocked servers for setting homes
* Players can still teleport to existing homes from blocked servers

---

## 📍 Last Location

TeleHop saves each player’s last logout location to MySQL.

Players can return to their last saved logout location across servers.

### Commands

* `/lastlocation`
* `/lastloc`
* `/backlast`
* `/ll`

### Features

* Saves location on quit
* Persists across restarts
* Works across servers
* Sends players to the correct backend server
* Admins can view, teleport to, or clear saved last locations

---

## 🌍 Warps

TeleHop supports admin warps and player warps.

### Admin Warps

Commands:

* `/warp <name>`
* `/setwarp <name>`
* `/delwarp <name>`
* `/warps`

Features:

* Global network warps
* Shared across all servers
* Stored in MySQL
* Per-warp permission support
* Cross-server teleporting

Example permission:

```text
telehop.warp.shop
```

### Player Warps

Commands:

* `/pwarp set <name>`
* `/pwarp del <name>`
* `/pwarp list`
* `/pwarp <name>`
* `/pwarp <player> <name>`
* `/pwarp public <name>`
* `/pwarp admin del <player> <name>`

Aliases:

* `/playerwarp`
* `/pwarps`

Features:

* Personal player warps
* Public or private toggle
* Cross-server teleporting
* Per-rank limits
* Admin delete command
* MySQL-backed storage

---

## 💀 Random Respawn

TeleHop can respawn players at a safe random location after death.

Features:

* Async safe-location search
* Paper HeightMap based lookup
* Configurable world
* Configurable radius
* Bed and respawn anchor respect
* Hub server skip support
* Per-server control through config
* Controlled through `config/respawn.yml`

---

## 🛠 Admin Commands

TeleHop includes admin commands for cross-server teleporting, warp management, home management, last-location tools, player data checks, and plugin control.

Admin commands are permission-gated and hidden from tab completion for players without access.

### TeleHop Management

| Command                | Description                             | Permission      |
| ---------------------- | --------------------------------------- | --------------- |
| `/telehop`             | Show TeleHop help                       | Everyone        |
| `/telehop help`        | Show categorised command help           | Everyone        |
| `/telehop version`     | Show the installed TeleHop version      | Everyone        |
| `/telehop ver`         | Alias for version                       | Everyone        |
| `/telehop reload`      | Reload config, messages, and warp cache | `telehop.admin` |
| `/telehop perms`       | List all permission nodes               | `telehop.admin` |
| `/telehop permissions` | Alias for permissions                   | `telehop.admin` |

### Admin Teleport

| Command                    | Description                                            | Permission       |
| -------------------------- | ------------------------------------------------------ | ---------------- |
| `/tp <player>`             | Teleport yourself to a player across servers           | `telehop.tp`     |
| `/tp <player1> <player2>`  | Teleport one player to another player across servers   | `telehop.tp`     |
| `/tp <x> <y> <z>`          | Teleport yourself to coordinates in your current world | `telehop.tp`     |
| `/tp <player> <x> <y> <z>` | Teleport a player to coordinates                       | `telehop.tp`     |
| `/tphere <player>`         | Pull a player to your location across servers          | `telehop.tphere` |

### Warp Admin

| Command                            | Description                                 | Permission      |
| ---------------------------------- | ------------------------------------------- | --------------- |
| `/setwarp <name>`                  | Create or update a global admin warp        | `telehop.admin` |
| `/delwarp <name>`                  | Delete a global admin warp                  | `telehop.admin` |
| `/listwarps`                       | List all player warps across all servers    | `telehop.admin` |
| `/listwarps <player>`              | List a specific player’s warps with details | `telehop.admin` |
| `/forcedelwarp <name>`             | Force-delete an admin warp                  | `telehop.admin` |
| `/forcedelwarp <player> <name>`    | Force-delete a specific player warp         | `telehop.admin` |
| `/pwarp admin del <player> <name>` | Delete any player’s personal warp           | `telehop.admin` |

### Home Admin

| Command                         | Description                                                      | Permission      |
| ------------------------------- | ---------------------------------------------------------------- | --------------- |
| `/forcedelhome <player>`        | List a player’s homes with clickable delete buttons              | `telehop.admin` |
| `/forcesethome <player> <name>` | Set a named home for another player at your current location     | `telehop.admin` |
| `/listhomes <player>`           | List a player’s homes with clickable teleport and delete buttons | `telehop.admin` |

### Last Location Admin

| Command                        | Description                                                                    | Permission      |
| ------------------------------ | ------------------------------------------------------------------------------ | --------------- |
| `/forcelastloc <player>`       | View a player’s last logout location, including server, world, and coordinates | `telehop.admin` |
| `/forcelastloc <player> tp`    | Teleport to a player’s saved last logout location                              | `telehop.admin` |
| `/forcelastloc <player> clear` | Clear a player’s saved last logout location                                    | `telehop.admin` |

Alias:

| Alias      | Command         |
| ---------- | --------------- |
| `/forcell` | `/forcelastloc` |

### Player Info

| Command                | Description                                                                             | Permission      |
| ---------------------- | --------------------------------------------------------------------------------------- | --------------- |
| `/playerinfo <player>` | Show a player’s TeleHop data, including homes, warps, last location, and current server | `telehop.admin` |

Alias:

| Alias    | Command       |
| -------- | ------------- |
| `/pinfo` | `/playerinfo` |

---

## 🔐 Permissions

Use `/telehop perms` in-game to view the full permission list.

### Player Permissions

| Permission             | Command           |
| ---------------------- | ----------------- |
| `telehop.spawn`        | `/spawn`          |
| `telehop.rtp`          | `/rtp`            |
| `telehop.tpa`          | `/tpa`            |
| `telehop.tpahere`      | `/tpahere`        |
| `telehop.tpa.accept`   | `/tpaaccept`      |
| `telehop.tpa.deny`     | `/tpadeny`        |
| `telehop.tpa.cancel`   | `/tpacancel`      |
| `telehop.tpa.toggle`   | `/tpatoggle`      |
| `telehop.warp`         | `/warp`, `/warps` |
| `telehop.pwarp`        | `/pwarp`          |
| `telehop.homes`        | `/home`           |
| `telehop.sethome`      | `/sethome`        |
| `telehop.delhome`      | `/delhome`        |
| `telehop.lastlocation` | `/lastlocation`   |
| `telehop.back`         | `/back`           |
| `telehop.back.death`   | `/back death`     |

### Home Limit Permissions

| Permission         | Effect   |
| ------------------ | -------- |
| `telehop.homes.1`  | 1 home   |
| `telehop.homes.2`  | 2 homes  |
| `telehop.homes.3`  | 3 homes  |
| `telehop.homes.4`  | 4 homes  |
| `telehop.homes.5`  | 5 homes  |
| `telehop.homes.6`  | 6 homes  |
| `telehop.homes.7`  | 7 homes  |
| `telehop.homes.8`  | 8 homes  |
| `telehop.homes.9`  | 9 homes  |
| `telehop.homes.10` | 10 homes |

TeleHop uses the highest matching home permission.

### Player Warp Limit Permissions

| Permission                | Effect                 |
| ------------------------- | ---------------------- |
| `telehop.warps.1`         | 1 player warp          |
| `telehop.warps.3`         | 3 player warps         |
| `telehop.warps.10`        | 10 player warps        |
| `telehop.warps.100`       | 100 player warps       |
| `telehop.warps.unlimited` | Unlimited player warps |

TeleHop uses the highest matching player warp permission.

### Per-Warp Access

| Permission            | Effect                       |
| --------------------- | ---------------------------- |
| `telehop.warp.<name>` | Access a specific admin warp |

Example:

```text
telehop.warp.shop
```

### Bypass Permissions

| Permission                   | Effect            |
| ---------------------------- | ----------------- |
| `telehop.rtp.bypasscooldown` | Skip RTP cooldown |
| `telehop.rtp.bypassdelay`    | Skip RTP warmup   |
| `telehop.tpa.bypasscooldown` | Skip TPA cooldown |

### Admin Permissions

| Permission                | Effect                                                                                                            |
| ------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| `telehop.admin`           | Main admin tools, warp management, home management, last-location tools, player info, reload, and permission list |
| `telehop.tp`              | Admin `/tp` commands                                                                                              |
| `telehop.tphere`          | Admin `/tphere` command                                                                                           |
| `telehop.back`            | Use `/back`                                                                                                       |
| `telehop.back.death`      | Use `/back death`                                                                                                 |
| `telehop.warps.unlimited` | Unlimited player warps                                                                                            |

### LuckPerms Example

```bash
# Default rank
lp group default permission set telehop.warps.3 true
lp group default permission set telehop.homes.2 true
lp group default permission set telehop.lastlocation true

# VIP rank
lp group vip permission set telehop.warps.10 true
lp group vip permission set telehop.homes.5 true

# MVP rank
lp group mvp permission set telehop.warps.unlimited true
lp group mvp permission set telehop.homes.10 true

# Staff rank
lp group staff permission set telehop.admin true
lp group staff permission set telehop.tp true
lp group staff permission set telehop.tphere true
lp group staff permission set telehop.back true
lp group staff permission set telehop.back.death true
lp group staff permission set telehop.warps.unlimited true
lp group staff permission set telehop.homes.10 true
```

---

## ⚡ Highlights

* Built for Velocity networks
* Supports Paper backend servers
* Paper 1.21.x and 26.1.x support
* Runtime version adapters
* MySQL-backed shared storage
* MariaDB support
* Optional Redis support for multi-proxy networks
* Region-aware hub routing
* Named homes
* Player warps
* Admin warps
* Cross-server TPA
* Cross-server `/back`
* Persistent last logout locations
* Random respawn
* RTP GUI
* Modular configuration
* Auto config migration
* Auto database migration
* Teleport effects
* Multi-language support
* LuckPerms support
* Network-wide tab completion
* Permission-gated admin suggestions
* In-game help
* In-game permission list

---

## 🧩 Multi-Server Support

TeleHop is designed for Velocity networks.

Paper handles teleport execution.

Velocity handles routing and player tracking.

MySQL stores shared network data.

Redis can synchronise multiple Velocity proxies when multi-proxy mode is enabled. TeleHop supports TLS connections to Redis with certificate and hostname verification.

This enables:

* Cross-server TPA
* Cross-server homes
* Cross-server warps
* Cross-server admin teleportation
* Cross-server `/back`
* Cross-server last-location teleportation
* Shared player tracking
* Shared player warp data
* Shared TPA requests
* Multi-proxy player lists
* Region-aware spawn routing

Both the Velocity plugin and Paper plugin are required.

---

## 📥 Requirements

| Software  | Version                                    |
| --------- | ------------------------------------------ |
| Paper     | 1.21.x or 26.1.x                           |
| Velocity  | 3.3+                                       |
| Java      | 21+ for Paper 1.21.x, 25+ for Paper 26.1.x |
| MySQL     | 8.0+                                       |
| MariaDB   | 10.5+                                      |
| LuckPerms | 5.4+ optional                              |
| Redis     | Optional, for multi-proxy mode             |

---

## ⚙️ Setup

1. Create a MySQL database and user.
2. Place `telehop-velocity-2.1.0.jar` in your Velocity plugins folder.
3. Place `telehop-paper-2.1.0.jar` in each Paper server plugins folder.
4. Start Velocity and the Paper servers once to generate files.
5. Stop the servers.
6. Configure MySQL credentials in `plugins/TeleHop/config/database.yml`.
7. Set each Paper server `server-name` in `plugins/TeleHop/config/general.yml`.
8. Set the hub server.
9. Start Velocity first.
10. Start all backend Paper servers.

Database tables are created automatically on first startup.

---

## 🔄 Updating

### From 1.0.0 or 1.0.2

* Replace the old Paper and Velocity JAR files.
* Start the network normally.
* Homes migrate from slot-based homes to named homes automatically.
* New config files are added automatically.
* Existing config files are not overwritten.
* Legacy `config.yml` migrates into the split config layout when needed.
* The old `config.yml` is renamed to `config.yml.old`.
* Add new home permissions if ranks need more than one home.

### From 2.0.0

* Update the Paper and Velocity JARs together.
* Add the `rtp.gui.ping` section to `config/rtp.yml` if you use customised configuration files.
* Add `rtp-in-progress` to customised language files.
* No database migration is required.

### Home Migration

Old homes convert into named homes during startup.

Example:

* Slot 1 becomes `Home 1`
* Slot 2 becomes `Home 2`
* Slot 3 becomes `Home 3`

No manual SQL changes are needed.

---

## 📁 Paper Configuration Layout

```text
plugins/TeleHop/
  config/
    general.yml
    database.yml
    features.yml
    teleport.yml
    tpa.yml
    rtp.yml
    home.yml
    respawn.yml
    WIKI.md
  storage.yml
  languages/
    en.yml
    nl.yml
    de.yml
    es.yml
    zh.yml
    pl.yml
```

### Config Files

| File           | Purpose                                                                |
| -------------- | ---------------------------------------------------------------------- |
| `general.yml`  | Server name, hub server, server list, language, messaging, and regions |
| `database.yml` | MySQL connection settings                                              |
| `features.yml` | Feature toggles                                                        |
| `teleport.yml` | Particles and sounds per teleport type                                 |
| `tpa.yml`      | Timeout, cooldown, warmup, and move cancellation                       |
| `rtp.yml`      | Random teleport regions, dimensions, cooldowns, warmups, GUI, and ping |
| `home.yml`     | Homes, GUI rows, bed colours, blocked servers, and labels              |
| `respawn.yml`  | Random respawn behaviour                                               |
| `WIKI.md`      | Generated config reference                                             |
| `storage.yml`  | Runtime data such as spawn location                                    |
| `languages/`   | Language files                                                         |

Most settings reload live with `/telehop reload`.

MySQL connection settings require a full server restart.

---

## 🌐 Velocity Configuration

Velocity uses `config.properties`.

Main settings include:

* MySQL host
* MySQL port
* MySQL database
* MySQL username
* MySQL password
* MySQL pool size
* Messaging dedupe window
* Request timeout
* Hub server
* Backend server list
* Proxy ID
* Multi-proxy toggle
* Redis host
* Redis port
* Redis password
* Redis channel prefix
* Redis TLS toggle
* Redis certificate and hostname verification
* Optional Redis CA certificate path

---

## 🗄 Database Tables

TeleHop creates and updates database tables automatically.

| Table            | Purpose                             |
| ---------------- | ----------------------------------- |
| `players`        | Tracks each player’s current server |
| `warps`          | Stores admin warps                  |
| `player_warps`   | Stores player warps                 |
| `tpa_requests`   | Stores active TPA requests          |
| `homes`          | Stores named player homes           |
| `last_locations` | Stores persistent logout locations  |

---

## 💬 Languages

TeleHop includes 6 built-in languages.

* English
* Dutch
* German
* Spanish
* Chinese
* Polish

English fallback is used when a message key is missing.

Messages support MiniMessage formatting and placeholders.

---

## 🧪 Building From Source

```bash
mvn clean package
```

Build outputs:

```text
paper/target/telehop-paper-2.1.0.jar
velocity/target/telehop-velocity-2.1.0.jar
```

Requires Java 21+ and Maven 3.8+.

The Paper 26.1.x adapter module compiles with Java 25 through Maven toolchains.

---

## ⚠ Compatibility

Supported:

* Paper
* Velocity
* MySQL
* MariaDB
* LuckPerms
* Redis for multi-proxy mode

Not supported:

* BungeeCord
* Hybrid servers
* Single-server-only setups without Velocity

---

## 👤 Author and Company

Developed by Epildev.

Company: Epildevconnect Ltd
Company number: 17247566
Registered in: England and Wales
Website: https://developer.epildevconnect.uk/
GitHub: https://github.com/GitEpildev
Discord: Epildev

---

## 📄 License

TeleHop is licensed under the MIT License with Additional Terms.

Attribution to Epildevconnect Ltd is required.

See the GitHub license file for full details.
