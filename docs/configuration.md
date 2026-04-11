# Configuration Reference

TeleHop uses **modular config files** on each Paper server, stored in `plugins/TeleHop/config/`:

| File | Purpose |
|------|---------|
| `config/general.yml` | Server identity, language, messaging, audit |
| `config/database.yml` | MySQL connection settings |
| `config/features.yml` | Feature on/off toggles |
| `config/teleport.yml` | Show-countdown, particles and sounds per teleport type |
| `config/tpa.yml` | TPA timeout, cooldown, delay, cancel-on-move |
| `config/rtp.yml` | RTP cooldown, delay, regions, dimensions, GUI |
| `config/home.yml` | Homes: slots, GUI, bed colours, blocked servers, world/server colours |
| `storage.yml` | Runtime-mutable values (spawn location). Written by the plugin. |

The split prevents `/telehop reload` or server restarts from overwriting values that were set in-game.

### Auto-Migration from Legacy `config.yml`

If the plugin detects a `config.yml` but no `config/general.yml`, it performs a one-time migration: splits the old file into the new modular layout and renames `config.yml` to `config.yml.old`.

> **Reload vs Restart:** Most settings can be reloaded live with `/telehop reload`. However, **MySQL connection settings** (`database.yml`) require a full server restart -- the connection pool is created once at startup.

---

## `config/general.yml`

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `language` | string | `"en"` | Language code. Available: `en`, `nl`, `de`, `es`, `zh`, `pl`. |
| `server-name` | string | `"lobby"` | Name of THIS server (must match `velocity.toml`) |
| `hub-server` | string | `"lobby"` | Server for `/spawn` (must match `velocity.toml`) |
| `servers` | map | `{lobby: "lobby"}` | All network servers. Key = label, value = Velocity name |
| `messaging.dedupe-window-ms` | long | `30000` | Packet dedup window (ms) |
| `messaging.request-timeout-ms` | long | `10000` | Cross-server response timeout (ms) |
| `audit.enabled` | boolean | `false` | Log teleports and admin actions |

## `config/database.yml`

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `mysql.host` | string | `"127.0.0.1"` | MySQL server IP/hostname |
| `mysql.port` | int | `3306` | MySQL port |
| `mysql.database` | string | `"telehop"` | Database name (same on all servers) |
| `mysql.username` | string | `"telehop"` | MySQL username |
| `mysql.password` | string | `"change-me"` | MySQL password |
| `mysql.pool-size` | int | `5` | Connection pool size |

## `config/features.yml`

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `features.spawn` | boolean | `true` | Enable/disable `/spawn` |
| `features.rtp` | boolean | `true` | Enable/disable `/rtp` |
| `features.tpa` | boolean | `true` | Enable/disable TPA commands |
| `features.warps` | boolean | `true` | Enable/disable admin warps |
| `features.player-warps` | boolean | `true` | Enable/disable player warps |
| `features.admin-tp` | boolean | `true` | Enable/disable `/tp`, `/tphere` |
| `features.homes` | boolean | `true` | Enable/disable `/home`, `/sethome` |
| `features.back` | boolean | `true` | Enable/disable `/back` |
| `features.tpa-toggle` | boolean | `true` | Enable/disable `/tpatoggle` |

When a feature is `false`, its commands still exist but display "This feature is disabled on this server."

## `config/teleport.yml`

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `show-countdown` | boolean | `true` | Show action-bar countdown during warmup |
| `effects.<type>.particle.enabled` | boolean | `true` | Show particles for this teleport type |
| `effects.<type>.particle.type` | string | `"PORTAL"` | Minecraft Particle enum name |
| `effects.<type>.particle.count` | int | `40` | Number of particles |
| `effects.<type>.sound.enabled` | boolean | `true` | Play sound for this teleport type |
| `effects.<type>.sound.type` | string | `"ENTITY_ENDERMAN_TELEPORT"` | Minecraft Sound enum name |
| `effects.<type>.sound.volume` | float | `1.0` | Volume (0.0-2.0) |
| `effects.<type>.sound.pitch` | float | `1.0` | Pitch (0.5-2.0) |

Supported `<type>` values: `default`, `spawn`, `tpa`, `rtp`, `warp`, `home`, `back`. If a type is not specified, the `default` settings are used.

## `config/tpa.yml`

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `tpa.timeout-seconds` | int | `60` | TPA request expiry time |
| `tpa.cooldown-seconds` | int | `10` | Time between TPA sends (0 = disabled) |
| `tpa.delay-seconds` | int | `0` | Warmup countdown after accept (0 = instant) |
| `tpa.cancel-on-move` | boolean | `true` | Cancel warmup if acceptor moves |

## `config/rtp.yml`

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `rtp.cooldown-seconds` | int | `30` | Time between /rtp uses |
| `rtp.delay-seconds` | int | `0` | Warmup countdown before RTP teleport |
| `rtp.cancel-on-move` | boolean | `true` | Cancel warmup if player moves |
| `rtp.max-radius` | int | `25000` | Max distance from 0,0 |
| `rtp.regions` | map | *(see below)* | RTP region definitions |
| `rtp.dimensions.*` | map | *(overworld, nether, end)* | World name mapping |
| `rtp.gui.*` | map | — | Region and dimension picker GUI settings |

### RTP Region Example

```yaml
rtp:
  regions:
    us:
      world: "world"
      radius: 5000
      gui:
        material: "RED_CONCRETE"
        name: "<red><bold>USA</bold>"
        lore:
          - "<gray>Random teleport on the US server"
```

## `config/home.yml`

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `homes.max-slots` | int | `5` | Maximum home slots (1-5) |
| `homes.confirm-set` | boolean | `true` | Show confirmation GUI before setting a home |
| `homes.gui-title` | string | `"<gradient:red:gold>Your Homes</gradient>"` | Homes GUI title (MiniMessage) |
| `homes.gui-rows` | int | `3` | Number of rows (3 = single chest). Beds are centered in the middle row. |
| `homes.bed-set` | string | `"LIME_BED"` | Bed material for occupied home slots |
| `homes.bed-empty` | string | `"RED_BED"` | Bed material for available (empty) slots |
| `homes.bed-locked` | string | `"LIGHT_BLUE_BED"` | Bed material for locked (no permission) slots |
| `homes.blocked-servers` | list | `["lobby"]` | Servers where `/sethome` is blocked. Players can still teleport to existing homes. |
| `homes.show-location` | boolean | `true` | Show x/y/z coordinates in the home GUI tooltip |
| `homes.world-colors.overworld` | string | `"<green>Overworld</green>"` | Display name for overworld dimensions (MiniMessage) |
| `homes.world-colors.nether` | string | `"<gradient:red:gold>Nether</gradient>"` | Display name for nether dimensions (MiniMessage) |
| `homes.world-colors.the-end` | string | `"<gradient:dark_purple:blue>The End</gradient>"` | Display name for end dimensions (MiniMessage) |
| `homes.server-colors.<name>` | string | — | Per-server colour/label in the home GUI. Key is server name (case-insensitive). |

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

## Velocity `config.properties`

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `mysql.host` | string | `127.0.0.1` | MySQL server IP (must match Paper) |
| `mysql.port` | int | `3306` | MySQL port |
| `mysql.database` | string | `telehop` | Database name (must match Paper) |
| `mysql.username` | string | `telehop` | MySQL username |
| `mysql.password` | string | `change-me` | MySQL password |
| `mysql.poolSize` | int | `5` | Connection pool size |
| `messaging.dedupeWindowMs` | long | `30000` | Packet dedup window (ms) |
| `messaging.requestTimeoutMs` | long | `10000` | Request timeout (ms) |
| `servers.hub` | string | `lobby` | Hub server name |
| `servers.backends` | string | `lobby` | Comma-separated server list |

---

## Paper `storage.yml`

Managed by the plugin -- do not edit manually unless setting spawn coordinates.

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `spawn.world` | string | `"world"` | World name for spawn |
| `spawn.x` | double | `0.5` | Spawn X |
| `spawn.y` | double | `100.0` | Spawn Y |
| `spawn.z` | double | `0.5` | Spawn Z |
| `spawn.yaw` | double | `0.0` | Spawn yaw |
| `spawn.pitch` | double | `0.0` | Spawn pitch |
