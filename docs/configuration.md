# Configuration reference

Paper uses `plugins/TeleHop/`. Choose `SINGLE`, `MULTI` or `CUSTOM` in the root `config.yml`. The first start creates the three profile directories. Only the selected directory is read during normal operation. Replace `<profile>` below with its lowercase directory name.

The [backend resource directory](../paper/src/main/resources/) shows the editable defaults using the same paths as the installed plugin. Open the [bundled setup guide](../paper/src/main/resources/config/WIKI.md) for step-by-step instructions or inspect a profile directly. [Single](../paper/src/main/resources/config/single/) · [Multi](../paper/src/main/resources/config/multi/) · [Custom](../paper/src/main/resources/config/custom/)

| Profile | Use |
| --- | --- |
| `SINGLE` | Standalone worlds, Multiverse or multiple backends on one Velocity proxy. Redis is optional infrastructure and the proxy does not connect to it in this mode. |
| `MULTI` | Multiple Velocity proxies using Redis and a shared database. Paper selects the regional hub using configured backend membership. |
| `CUSTOM` | Explicit control through the backend's `multi-proxy.enabled` and the proxy's `proxy.multi-proxy-enabled`. |

The root accepts `config-version: 1`, `platform.detection: AUTO`, `commands.custom-names.enabled`, `commands.additional-commands.enabled`, `migration.automatic`, `migration.backup-before-update` and `migration.backup-retention`. Automatic migration and backups default to true. New installations leave both command customisation switches off. Console detail is controlled by the booleans under `logging`: `debug`, `configuration-validation`, `migration` and `platform-detection`.

| Path | Contents |
| --- | --- |
| `config/<profile>/general.yml` | Language, backend identity, global hub, server labels, audit and CUSTOM multi-proxy switch |
| `config/multi/messaging.yml`, `config/custom/messaging.yml` | Plugin messaging switch, deduplication window and request timeout. In SINGLE these fields live in `config/single/general.yml`. |
| `config/multi/regions.yml`, `config/custom/regions.yml` | Regional server membership and regional hubs. SINGLE retains the `regions` mapping in `config/single/general.yml`. |
| `config/<profile>/spawns.yml` | Named destinations and world or backend spawn routes |
| `config/<profile>/features.yml` | Per-backend features and optional hub registration |
| `config/<profile>/teleport.yml` | Countdown, particles and sounds |
| `config/<profile>/tpa.yml` | TPA timeout, cooldown, delay and movement cancellation |
| `config/<profile>/rtp.yml` | RTP regions, worlds, search radii and menus |
| `config/<profile>/respawn.yml` | Death respawn settings, beds, anchors and excluded worlds |
| `config/<profile>/home.yml` | Home slots, menus and backend restrictions |
| `config/database.yml`, `config/WIKI.md` | Shared database settings and bundled guide |
| `commands/`, `languages/` | Command groups and messages shared across profiles |
| `storage.yml` | This backend's runtime spawn position |
| `migrations/` | Migration state, history, private backups and any pending journal |

### Identity, messaging and routes

Set `server-name` to the registered name of this backend behind Velocity. Fresh installs derive an initial name from the server directory and use it as the global hub. Set `hub-server` to the desired backend. `servers` maps display labels to backend names. `language` defaults to `en`. Enable `audit.enabled` to log teleport actions.

For SINGLE edit `config/single/general.yml`. MULTI and CUSTOM use their own `messaging.yml` and `regions.yml`. Online-mode backends automatically disable the plugin channel. On an offline-mode standalone backend, set `messaging.network-enabled: false`. Behind a trusted proxy, restrict network access to the backend port. `messaging.request-timeout-ms` defaults to 10000 and `messaging.dedupe-window-ms` defaults to 30000. Set `regions.<name>.servers` and `regions.<name>.hub` for regional routing. You choose the region and backend identifiers. [Named and world routes](spawns.md)

Use `/telehop config validate` to check the candidate and `/telehop reload` or `/telehop reload config` to apply reloadable settings. Use `/telehop reload messages` to refresh only the running language and English fallback. A failed validation keeps the running configuration. Restart after changing the active profile, database, network transport or timings, server identity or registered command labels. [Migration and recovery](migration.md)

## `config/database.yml`

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `mysql.host` | string | `"127.0.0.1"` | MySQL server IP/hostname |
| `mysql.port` | int | `3306` | MySQL port |
| `mysql.database` | string | `"telehop"` | Database name (same on all servers) |
| `mysql.username` | string | `"telehop"` | MySQL username |
| `mysql.password` | string | `"change-me"` | MySQL password |
| `mysql.pool-size` | int | `5` | Connection pool size |

## `config/<profile>/features.yml`

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `features.hub` | boolean | `false` | Register the hub command and its configured aliases on this backend after a Paper restart |
| `features.spawn` | boolean | `true` | Enable/disable `/spawn` |
| `features.rtp` | boolean | `true` | Enable/disable `/rtp` |
| `features.tpa` | boolean | `true` | Enable/disable TPA commands |
| `features.warps` | boolean | `true` | Enable/disable admin warps |
| `features.player-warps` | boolean | `true` | Enable/disable player warps |
| `features.admin-tp` | boolean | `true` | Enable/disable `/tp`, `/tphere` |
| `features.homes` | boolean | `true` | Enable/disable `/home`, `/sethome` |
| `features.back` | boolean | `true` | Enable/disable `/back` |
| `features.tpa-toggle` | boolean | `true` | Enable/disable `/tpatoggle` |
| `features.random-respawn` | boolean | `true` | Enable random death respawn on this backend |
| `features.last-location` | boolean | `true` | Enable/disable `/lastlocation` |

The hub switch controls command registration at startup. Upgrades inherit `hub-command.enabled` from the old `spawns.yml` when the new switch is missing. For the other switches, when a feature is `false`, its commands still exist but display "This feature is disabled on this server."

## `config/<profile>/teleport.yml`

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `show-countdown` | boolean | `true` | Show a centred title countdown during warmup |
| `effects.<type>.particle.enabled` | boolean | `true` | Show particles for this teleport type |
| `effects.<type>.particle.type` | string | `"PORTAL"` | Minecraft Particle enum name |
| `effects.<type>.particle.count` | int | `40` | Number of particles |
| `effects.<type>.sound.enabled` | boolean | `true` | Play sound for this teleport type |
| `effects.<type>.sound.type` | string | `"ENTITY_ENDERMAN_TELEPORT"` | Minecraft Sound enum name |
| `effects.<type>.sound.volume` | float | `1.0` | Volume (0.0-2.0) |
| `effects.<type>.sound.pitch` | float | `1.0` | Pitch (0.5-2.0) |

Supported `<type>` values: `default`, `spawn`, `tpa`, `rtp`, `warp`, `home`, `back`. If a type is not specified, the `default` settings are used.

## `config/<profile>/tpa.yml`

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `tpa.timeout-seconds` | int | `60` | TPA request expiry time |
| `tpa.cooldown-seconds` | int | `10` | Time between TPA sends (0 = disabled) |
| `tpa.delay-seconds` | int | `0` | Warmup countdown after accept (0 = instant) |
| `tpa.cancel-on-move` | boolean | `true` | Cancel warmup if acceptor moves |

## `config/<profile>/rtp.yml`

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `rtp.cooldown-seconds` | int | `30` | Time between /rtp uses |
| `rtp.delay-seconds` | int | `0` | Warmup countdown before RTP teleport |
| `rtp.cancel-on-move` | boolean | `true` | Cancel warmup if player moves |
| `rtp.max-radius` | int | `25000` | Max distance from 0,0 |
| `rtp.regions` | map | `{}` | RTP region definitions |
| `rtp.dimensions` | map | `{}` | Map dimension IDs to loaded world names. An empty map discovers the loaded worlds. |
| `rtp.default-region` | string | First region | Region for ordinary arrivals with no region selector |
| `rtp.default-dimension` | string | First dimension | Dimension used when a direct command or ordinary arrival omits a world and dimension |
| `rtp.gui.select-dimension-without-region` | boolean | `true` in fresh profiles | Open the dimension selector when no regions exist. An omitted setting preserves direct travel. |
| `rtp.gui.dimension-items.<id>` | map | Grass block labelled with the ID | Display material, name and lore for a configured dimension |
| `rtp.gui.*` | map | - | Region and dimension picker GUI settings |
| `rtp.gui.ping.enabled` | boolean | `true` | Show estimated destination ping on region items, refreshed twice per second while the menu is open |
| `rtp.gui.ping.proxy-ping` | boolean | `true` | Include the proxy-to-destination leg in the estimate (measured by Velocity every 10s). `false` shows only the player's current ping |

### RTP Region Example

```yaml
rtp:
  default-dimension: surface
  dimensions: {surface: woodland, caves: old_mines}
  regions:
    quarry:
      world: quarry_floor
      radius: 5000
      gui:
        material: STONE_PICKAXE
        name: "<gold>Quarry</gold>"
        lore: ["<gray>Find a safe spot in the quarry"]
```

## `config/<profile>/respawn.yml`

Leave `random-respawn.world` empty to search the world where the player died. Enter a loaded world name or namespaced world key to select a fixed destination. A missing configured world leaves the ordinary respawn in place. `random-respawn.excluded-worlds` starts empty and accepts exact names or prefixes ending in `*`. Existing world selections and exclusions carry through an upgrade. A standalone server can use random respawn with its default identity. A backend acting as the network hub retains ordinary respawns.

## `config/<profile>/home.yml`

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `homes.max-slots` | int | `10` | Maximum home slots (1-10) |
| `homes.confirm-set` | boolean | `true` | Retained for compatibility. The homes menu saves an empty slot when clicked and confirms deletion separately. |
| `homes.gui-title` | string | `"<gradient:red:gold>Your Homes</gradient>"` | Homes GUI title (MiniMessage) |
| `homes.gui-rows` | int | `5` | Inventory rows (1-6). Ten beds require at least two rows. When space permits, a blank row separates the two groups of beds. |
| `homes.bed-set` | string | `"LIME_BED"` | Bed material for occupied home slots |
| `homes.bed-empty` | string | `"RED_BED"` | Bed material for available (empty) slots |
| `homes.bed-locked` | string | `"LIGHT_BLUE_BED"` | Bed material for locked (no permission) slots |
| `homes.blocked-servers` | list | `[]` | Servers where `/sethome` is blocked (exact match). Players can still teleport to existing homes. |
| `homes.blocked-server-prefixes` | list | `[]` | Block `/sethome` on servers whose name starts with any of these strings (e.g. `"lobby"` matches `lobby-usa`, `lobby-eu`). |
| `homes.show-location` | boolean | `true` | Show x/y/z coordinates in the home GUI tooltip |
| `homes.world-colors.overworld` | string | `"<green>Overworld</green>"` | Display name for overworld dimensions (MiniMessage) |
| `homes.world-colors.nether` | string | `"<gradient:red:gold>Nether</gradient>"` | Display name for nether dimensions (MiniMessage) |
| `homes.world-colors.the-end` | string | `"<gradient:dark_purple:blue>The End</gradient>"` | Display name for end dimensions (MiniMessage) |
| `homes.server-colors.<name>` | string | - | Per-server colour/label in the home GUI. Key is server name (case-insensitive). |

### Bed Colours

Use any Minecraft bed material: `WHITE_BED`, `ORANGE_BED`, `MAGENTA_BED`, `LIGHT_BLUE_BED`, `YELLOW_BED`, `LIME_BED`, `PINK_BED`, `GRAY_BED`, `LIGHT_GRAY_BED`, `CYAN_BED`, `PURPLE_BED`, `BLUE_BED`, `BROWN_BED`, `GREEN_BED`, `RED_BED`, `BLACK_BED`.

### Server Colours Example

```yaml
homes:
  server-colors:
    lobby: "<gray>Lobby"
    usa: "<red>USA"
    eu: "<blue>EU"
```

Any server not listed displays in plain white.

---

## Velocity profiles

The proxy has a separate root selector, with shared database and Redis settings. [Velocity configuration](velocity.md)

## Paper `storage.yml`

On a new backend the saved default is initialised from the first loaded world's actual spawn. TeleHop records the world, X, Y, Z, yaw and pitch under `spawn`. Existing `storage.yml` or a migrated legacy spawn takes precedence. Use `/telehop spawn setdefault` in game to replace this location and publish the new coordinates to the shared directory.

## Command names and aliases

Set `commands.custom-names.enabled: true` in the root selector. Under `commands/`, edit the appropriate group's built-in command key and enable its `custom-name`. For example, in `commands/teleport.yml`:

```yaml
tp:
  custom-name:
    enabled: true
    name: networktp
    aliases: [ntp]
```

Restart the backend after changing registered names or aliases. Permissions follow the original action. `/telehop` remains available for administrative recovery. [Command reference](commands.md)
