# Configuration Reference

## Paper `config.yml`

> **Reload vs Restart:** Most settings (language, feature toggles, RTP, TPA, spawn) can be reloaded live with `/telehop reload`. However, **MySQL connection settings** (`mysql.*`) require a full server restart — the connection pool is created once at startup and is not recreated on reload.

### Language

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `language` | string | `"en"` | Language code for player-facing messages. Available: `en`, `nl`, `de`, `es`, `zh`, `pl`. Custom codes supported if matching `.yml` exists in `languages/`. |

### Feature Toggles

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `features.spawn` | boolean | `true` | Enable/disable `/spawn` |
| `features.rtp` | boolean | `true` | Enable/disable `/rtp` |
| `features.tpa` | boolean | `true` | Enable/disable `/tpa`, `/tpahere`, `/tpaaccept`, `/tpadeny`, `/tpacancel` |
| `features.warps` | boolean | `true` | Enable/disable admin warps (`/warp`, `/setwarp`, `/delwarp`, `/warps`) |
| `features.player-warps` | boolean | `true` | Enable/disable player warps (`/pwarp`) |
| `features.admin-tp` | boolean | `true` | Enable/disable `/tp`, `/tphere` |

When a feature is `false`, its commands still exist but display "This feature is disabled on this server." This avoids confusing "unknown command" errors.

Reloadable with `/telehop reload`.

### Server Identity

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `server-name` | string | `"lobby"` | Name of THIS server (must match `velocity.toml`) |
| `hub-server` | string | `"lobby"` | Server for `/spawn` (must match `velocity.toml`) |
| `servers` | map | `{lobby: "lobby"}` | All network servers. Key = label, value = Velocity name |
| `mysql.host` | string | `"127.0.0.1"` | MySQL server IP/hostname |
| `mysql.port` | int | `3306` | MySQL port |
| `mysql.database` | string | `"telehop"` | Database name (same on all servers) |
| `mysql.username` | string | `"telehop"` | MySQL username |
| `mysql.password` | string | `"change-me"` | MySQL password |
| `mysql.pool-size` | int | `5` | Connection pool size (5 small, 10 large) |
| `messaging.dedupe-window-ms` | long | `30000` | Packet dedup window (ms) |
| `messaging.request-timeout-ms` | long | `10000` | Cross-server response timeout (ms) |
| `tpa.timeout-seconds` | int | `60` | TPA request expiry time |
| `tpa.cooldown-seconds` | int | `10` | Time between TPA sends (0 = disabled) |
| `tpa.delay-seconds` | int | `0` | Warmup countdown after `/tpaaccept` before the teleport fires (0 = instant). The acceptor must stand still during the countdown. |
| `tpa.cancel-on-move` | boolean | `true` | Cancel TPA warmup if the acceptor moves. Only applies when `tpa.delay-seconds > 0`. |
| `rtp.cooldown-seconds` | int | `30` | Time between /rtp uses (0 = disabled) |
| `rtp.delay-seconds` | int | `0` | Warmup countdown before RTP teleport (0 = instant). Player must stand still during the countdown or it cancels. Bypassed with `telehop.rtp.bypassdelay`. |
| `rtp.cancel-on-move` | boolean | `true` | Cancel RTP warmup if the player moves. Only applies when `rtp.delay-seconds > 0`. |
| `rtp.max-radius` | int | `25000` | Max distance from 0,0 for RTP (-1 = unlimited) |
| `rtp.regions` | map | *(see below)* | RTP region definitions |
| `rtp.dimensions.overworld` | string | `"world"` | Overworld world name |
| `rtp.dimensions.nether` | string | `"world_nether"` | Nether world name |
| `rtp.dimensions.end` | string | `"world_the_end"` | End world name |
| `rtp.gui.region-menu.title` | string | `"Select Region"` | Region picker GUI title |
| `rtp.gui.region-menu.rows` | int | `3` | Region picker rows (1-6) |
| `rtp.gui.dimension-menu.title` | string | `"Select Dimension"` | Dimension picker GUI title |
| `rtp.gui.dimension-menu.rows` | int | `3` | Dimension picker rows (1-6) |
| `spawn.location.*` | — | `0.5, 100, 0.5` | Spawn point (world, x, y, z, yaw, pitch) |
| `teleport.show-countdown` | boolean | `true` | Show an action-bar countdown (5... 4... 3...) during RTP and TPA warmup delays. The format is controlled by the `countdown-actionbar` language key. |
| `audit.enabled` | boolean | `false` | Log teleports and admin actions |

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
    eu:
      world: "world"
      radius: 5000
      gui:
        material: "BLUE_CONCRETE"
        name: "<aqua><bold>EU</bold>"
        lore:
          - "<gray>Random teleport on the EU server"
```

One region = no picker GUI. Two+ regions = region picker opens first.

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
| `servers.hub` | string | `lobby` | Hub server name (must match `velocity.toml`) |
| `servers.backends` | string | `lobby` | Comma-separated server list |
