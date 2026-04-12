# TeleHop — Configuration Wiki

> **Plugin version:** 1.0.2  
> **Config folder:** `plugins/TeleHop/config/`  
> **Language folder:** `plugins/TeleHop/languages/`

All config files and this wiki are extracted automatically on first run.  
Settings marked **Reloadable** take effect with `/telehop reload` — no restart needed.  
Settings marked **⚠ Restart required** are only read once at startup.

---

## Table of contents

1. [Quick-start checklist](#quick-start-checklist)
2. [general.yml](#generalyml)
3. [database.yml](#databaseyml)
4. [features.yml](#featuresyml)
5. [tpa.yml](#tpayml)
6. [rtp.yml](#rtpyml)
7. [home.yml](#homeyml)
8. [respawn.yml](#respawnyml)
9. [teleport.yml](#teleportyml)
10. [Permissions](#permissions)
11. [Admin commands](#admin-commands)
12. [Language files](#language-files)
13. [Upgrading from a previous version](#upgrading-from-a-previous-version)
14. [Typical multi-server setup](#typical-multi-server-setup)

---

## Quick-start checklist

1. Set `server-name` in `general.yml` — must match this server's name in `velocity.toml`.
2. Set `hub-server` in `general.yml` — the server players reach with `/spawn`.
3. Fill in MySQL credentials in `database.yml` — every server shares the same database.
4. Toggle features on/off in `features.yml` to match what this server should offer.
5. Start the server — TeleHop creates all required database tables automatically.
6. Assign permissions with LuckPerms (see [Permissions](#permissions)).

---

## general.yml

Identity, language, and cross-server messaging.  
**Reloadable.**

| Key | Default | Description |
|-----|---------|-------------|
| `language` | `en` | Language code for all player-facing messages. Built-in: `en` `nl` `de` `es` `zh` `pl`. Add a custom `<code>.yml` to `languages/` for your own locale. Missing keys fall back to `en`. |
| `server-name` | `lobby` | **Must** match this server's name in Velocity's `velocity.toml [servers]` block. Every Paper server needs a unique value. |
| `hub-server` | `lobby` | The server players are sent to when they run `/spawn`. Must match a server name in `velocity.toml`. |
| `servers` | *(map)* | All Paper servers running TeleHop on your network. Format: `<label>: "<velocity-name>"`. Used for cross-server lookups and RTP region routing. |
| `messaging.dedupe-window-ms` | `30000` | Duplicate plugin-message packets received within this window (ms) are ignored. Rarely needs changing. |
| `messaging.request-timeout-ms` | `10000` | How long (ms) to wait for a cross-server packet response before giving up. |
| `audit.enabled` | `false` | Logs every teleport event to the server console. Useful for debugging; noisy in production. |

---

## database.yml

Shared MySQL / MariaDB connection used by all servers and the Velocity proxy.  
**⚠ Restart required** — the connection pool is built once at startup.

| Key | Default | Description |
|-----|---------|-------------|
| `mysql.host` | `127.0.0.1` | IP address or hostname of your MySQL server. |
| `mysql.port` | `3306` | MySQL port. |
| `mysql.database` | `telehop` | Database name. Create it first: `CREATE DATABASE telehop;` |
| `mysql.username` | `telehop` | Database user. Requires `SELECT`, `INSERT`, `UPDATE`, `DELETE`, `CREATE TABLE`. |
| `mysql.password` | `change-me` | Database password. |
| `mysql.pool-size` | `5` | HikariCP connection pool size. `5` for small networks (≤50 players), `10` for larger ones. |

> **Every Paper server and the Velocity proxy must use the same database credentials.**  
> TeleHop creates its tables automatically on first startup.

---

## features.yml

Turn entire feature modules on or off per server.  
**Reloadable.**

When a feature is `false`, its commands still respond gracefully ("this feature is disabled") instead of showing "unknown command" to players.

| Key | Default | What it controls |
|-----|---------|-----------------|
| `spawn` | `true` | `/spawn` — send player to the hub server |
| `rtp` | `true` | `/rtp` — random teleport with region/dimension GUI |
| `tpa` | `true` | `/tpa`, `/tpahere`, `/tpaaccept`, `/tpadeny`, `/tpacancel` |
| `warps` | `true` | `/warp`, `/setwarp`, `/delwarp`, `/warps` — admin-managed warps |
| `player-warps` | `true` | `/pwarp` — player-created personal warps |
| `admin-tp` | `true` | `/tp`, `/tphere` — admin cross-server teleport |
| `homes` | `true` | `/home`, `/sethome` — player homes with GUI |
| `back` | `true` | `/back`, `/back death` — return to previous location |
| `tpa-toggle` | `true` | `/tpatoggle` — let players block incoming TPA requests |
| `random-respawn` | `true` | Randomize death respawn location (configured in `respawn.yml`) |

**Recommended settings per server type:**

| Server type | Suggested disabled |
|-------------|-------------------|
| Lobby / Hub | `rtp`, `random-respawn` (homes are blocked via `home.yml` blocked-servers) |
| Survival | All enabled |
| Minigame | `tpa`, `warps`, `homes`, `back`, `random-respawn` |

---

## tpa.yml

Controls TPA request timing.  
**Reloadable.**

| Key | Default | Description |
|-----|---------|-------------|
| `tpa.timeout-seconds` | `60` | Seconds before an unanswered request auto-expires. |
| `tpa.cooldown-seconds` | `10` | Cooldown between sending requests. `0` = no cooldown. Bypass permission: `telehop.tpa.bypasscooldown`. |
| `tpa.delay-seconds` | `0` | Warmup countdown after accepting before the teleport fires. `0` = instant. |
| `tpa.cancel-on-move` | `true` | Moving during warmup cancels the teleport. Only applies when `delay-seconds > 0`. |

---

## rtp.yml

Controls `/rtp` — the random teleport command with region and dimension picker GUIs.  
**Reloadable.**

### Timing

| Key | Default | Description |
|-----|---------|-------------|
| `rtp.cooldown-seconds` | `30` | Cooldown between uses. `0` = no cooldown. Bypass: `telehop.rtp.bypasscooldown`. |
| `rtp.delay-seconds` | `0` | Warmup before teleporting. `0` = instant. Bypass: `telehop.rtp.bypassdelay`. |
| `rtp.cancel-on-move` | `true` | Moving during warmup cancels the teleport. Only applies when `delay-seconds > 0`. |
| `rtp.max-radius` | `25000` | Hard cap on random coordinates. Region radii are capped to this value. |

### Regions

Regions are shown in a picker GUI when there are two or more. With a single region, players skip straight to the dimension selector.

```yaml
regions:
  <name>:
    world: "world"             # World folder name on this server
    radius: 25000              # Max distance from 0,0 on X and Z
    gui:
      material: "GRASS_BLOCK" # Any Bukkit Material name
      name: "<green>Name"     # MiniMessage display name
      lore:
        - "<gray>Description"
```

### Dimensions

Maps the three dimension labels to the actual world folder names on this server:

```yaml
dimensions:
  overworld: "world"
  nether: "world_nether"
  end: "world_the_end"
```

### GUI appearance

```yaml
gui:
  region-menu:
    title: "<dark_purple>Select Region</dark_purple>"
    rows: 3    # 1–6
  dimension-menu:
    title: "<gold>Select Dimension</gold>"
    rows: 3
```

---

## home.yml

Controls `/home` and `/sethome`.  
**Reloadable.**

| Key | Default | Description |
|-----|---------|-------------|
| `homes.max-slots` | `5` | Maximum home slots available. Players unlock slots via permissions (see below). |
| `homes.confirm-set` | `true` | Opens a confirmation GUI before setting a home. `false` = set immediately on slot click. |
| `homes.gui-title` | *(gradient)* | Title of the `/home` chest GUI. Supports MiniMessage. |
| `homes.gui-rows` | `3` | Rows in the GUI (`1`–`6`). |
| `homes.bed-set` | `LIME_BED` | Bed material for a slot that has a home saved. |
| `homes.bed-empty` | `RED_BED` | Bed material for an available but empty slot. |
| `homes.bed-locked` | `LIGHT_BLUE_BED` | Bed material for a slot the player has no permission for. |
| `homes.blocked-servers` | `[lobby]` | Servers where new homes **cannot be set**. Opening the GUI and teleporting to existing homes still works. Server names are case-insensitive. |
| `homes.show-location` | `true` | Show X/Y/Z coordinates in the home slot tooltip. Set `false` for a cleaner look. |
| `homes.world-colors.*` | *(map)* | MiniMessage label for each dimension in the GUI. Keys: `overworld`, `nether`, `the-end`. |
| `homes.server-colors.*` | *(map)* | MiniMessage label per server name shown in the GUI. Unlisted servers display in plain white. |

### Home slot permissions

Assign only the **highest** slot count a rank should have — TeleHop picks the highest matching permission automatically.

| Permission | Slots | Default |
|------------|-------|---------|
| `telehop.homes.1` | 1 | Everyone |
| `telehop.homes.2` | 2 | — |
| `telehop.homes.3` | 3 | — |
| `telehop.homes.4` | 4 | — |
| `telehop.homes.5` | 5 | — |

Example LuckPerms setup:
```
lp group default permission set telehop.homes.1
lp group vip    permission set telehop.homes.3
lp group mvp    permission set telehop.homes.5
```

---

## respawn.yml

Controls random death respawn.  
**Reloadable.**  
Enable or disable the entire feature in `features.yml` under `random-respawn`.

| Key | Default | Description |
|-----|---------|-------------|
| `random-respawn.world` | `world` | World that safe locations are searched in. Must be loaded on this server. |
| `random-respawn.radius` | `5000` | Max distance from 0,0 on both X and Z axes. A coordinate is chosen randomly in `[-radius, +radius]`. |
| `random-respawn.respect-bed-spawn` | `true` | If `true` and the player's bed is accessible, they respawn at their bed instead of randomly. Set `false` to always randomize. |
| `random-respawn.respect-anchor-spawn` | `true` | If `true` and the player's respawn anchor is charged, they respawn there instead of randomly. Set `false` to always randomize. |

### How it works

The safe-location search runs **asynchronously** the moment a player dies — while they're looking at the death screen. By the time they click "Respawn" the location is already found and waiting. The main server thread is never blocked.

The system reuses the same safety checks as `/rtp`:
- Solid non-lethal floor (no lava, magma, cactus, fire, etc.)
- Two air blocks above (room to stand)
- Sky access in the overworld (rejects caves and underground pockets)
- No adjacent water or lava at foot level
- Minimum Y of 50 in the overworld

**Fallback:** If no safe spot is found within 300 attempts, the player respawns at the world spawn point and a warning is logged to console.

**Hub server exempt:** If `server-name` matches `hub-server` in `general.yml`, random respawn is completely skipped on that server — players are sent to the set spawn point as normal. This is hardcoded and cannot be overridden by config; it ensures the lobby always behaves predictably regardless of what `features.yml` says.

**Bypass:** Players with `telehop.respawn.bypass` always use the default respawn location.

---

## teleport.yml

Controls visual effects and action bar countdown for all teleport types.  
**Reloadable.**

| Key | Default | Description |
|-----|---------|-------------|
| `show-countdown` | `true` | Show an action bar countdown (5… 4… 3…) during any warmup delay. |
| `effects.<type>.particle.enabled` | `true` | Toggle particles for this teleport type. |
| `effects.<type>.particle.type` | varies | Minecraft `Particle` enum name (e.g. `PORTAL`, `HEART`, `ENCHANT`). |
| `effects.<type>.particle.count` | varies | Number of particles to spawn. |
| `effects.<type>.sound.enabled` | `true` | Toggle the sound effect. |
| `effects.<type>.sound.type` | varies | Minecraft `Sound` enum name (e.g. `ENTITY_ENDERMAN_TELEPORT`). |
| `effects.<type>.sound.volume` | `1.0` | Volume (`0.0`–`2.0`). |
| `effects.<type>.sound.pitch` | `1.0` | Pitch (`0.5`–`2.0`). |

**Available effect types:** `default` `spawn` `tpa` `rtp` `warp` `home` `back`

The `default` effect is used as a fallback for any type not explicitly defined.

---

## Permissions

Assign via LuckPerms: `lp user/group <name> permission set <node> [true/false]`

### Player permissions (default: everyone)

| Node | Description |
|------|-------------|
| `telehop.spawn` | `/spawn` |
| `telehop.rtp` | `/rtp` |
| `telehop.warp` | `/warp`, `/warps` |
| `telehop.pwarp` | `/pwarp` |
| `telehop.tpa` | `/tpa` |
| `telehop.tpahere` | `/tpahere` |
| `telehop.tpa.accept` | `/tpaaccept` |
| `telehop.tpa.deny` | `/tpadeny` |
| `telehop.tpa.cancel` | `/tpacancel` |
| `telehop.tpa.toggle` | `/tpatoggle` |
| `telehop.homes` | `/home`, `/sethome` |
| `telehop.homes.1` – `telehop.homes.5` | Home slot count (assign only the highest needed) |

### Admin permissions (default: OP)

| Node | Description |
|------|-------------|
| `telehop.admin` | `/setwarp`, `/delwarp`, `/listwarps`, `/forcedelwarp`, `/forcedelhome`, `/telehop reload\|perms\|version\|help` |
| `telehop.tp` | `/tp` — admin cross-server teleport |
| `telehop.tphere` | `/tphere` — pull a player to you |
| `telehop.back` | `/back` — return to last teleport location |
| `telehop.back.death` | `/back death` — return to death location |

### Bypass permissions (default: OP)

| Node | Description |
|------|-------------|
| `telehop.rtp.bypasscooldown` | Skip the `/rtp` cooldown |
| `telehop.rtp.bypassdelay` | Skip the `/rtp` warmup countdown |
| `telehop.tpa.bypasscooldown` | Skip the TPA send cooldown |
| `telehop.warps.unlimited` | No player warp slot limit |
| `telehop.respawn.bypass` | Always respawn at default location, skipping random respawn |

### Per-warp access

| Node | Description |
|------|-------------|
| `telehop.warp.<name>` | Access a specific admin warp by name (case-insensitive) |

---

## Admin commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/telehop reload` | `telehop.admin` | Reload all config and language files |
| `/telehop version` | `telehop.admin` | Show the running plugin version |
| `/telehop help` | `telehop.admin` | List all commands |
| `/telehop perms` | `telehop.admin` | List all permission nodes |
| `/setwarp <name>` | `telehop.admin` | Create or update a global warp at your location |
| `/delwarp <name>` | `telehop.admin` | Delete a global warp |
| `/listwarps [player]` | `telehop.admin` | List all player warps (optionally filtered by player) |
| `/forcedelwarp <name>` | `telehop.admin` | Force-delete any admin or player warp |
| `/forcedelhome <player> <slot>` | `telehop.admin` | Force-delete a specific home slot for any player |
| `/tp <player>` | `telehop.tp` | Admin cross-server teleport to any online player |
| `/tphere <player>` | `telehop.tphere` | Pull an online player to your current server and location |

---

## Language files

Located in `plugins/TeleHop/languages/`. All message values use [MiniMessage](https://docs.advntr.dev/minimessage/) format.

**Built-in languages:** `en` (English) · `nl` (Dutch) · `de` (German) · `es` (Spanish) · `zh` (Chinese) · `pl` (Polish)

**Adding a custom language:**
1. Copy `en.yml` to `<code>.yml` (e.g. `fr.yml`)
2. Translate the values — leave keys you haven't translated and they fall back to English automatically
3. Set `language: "fr"` in `general.yml`
4. Run `/telehop reload`

**Available MiniMessage tags:** `<red>`, `<bold>`, `<gradient:red:gold>`, `<click:run_command:'/cmd'>`, `<hover:show_text:'...'>`, and more — see the MiniMessage docs linked above.

---

## Upgrading from a previous version

### From 1.0.0 / 1.0.1 → 1.0.2

1. Replace `telehop-paper-1.0.2.jar` in your `plugins/` folder.
2. On first start, TeleHop will extract any **new** config files (`respawn.yml`, `WIKI.md`) into `plugins/TeleHop/config/` without touching your existing files.
3. Manually add `random-respawn: true` to your `features.yml` if you want the feature enabled (it defaults to `true` on fresh installs only).
4. Optionally review `respawn.yml` to set your preferred world and radius.
5. No database changes are required.

### From a pre-split monolithic config.yml

If your `plugins/TeleHop/` folder still has a `config.yml` (single file) from a very early version, TeleHop will automatically migrate it to the split config folder on first run and rename the old file to `config.yml.old`. No manual steps needed.

---

## Typical multi-server setup

```
Velocity proxy
├── lobby  (hub-server)
├── us
└── eu

── velocity.toml ──────────────────────────────────────
[servers]
lobby = "127.0.0.1:25566"
us    = "104.192.7.118:25565"
eu    = "77.68.83.81:25565"

── plugins/TeleHop/config/general.yml (Lobby) ─────────
server-name: "lobby"
hub-server:  "lobby"
servers:
  lobby: "lobby"
  us: "us"
  eu: "eu"

── plugins/TeleHop/config/general.yml (US) ────────────
server-name: "us"
hub-server:  "lobby"
servers:
  lobby: "lobby"
  us: "us"
  eu: "eu"

── plugins/TeleHop/config/general.yml (EU) ────────────
server-name: "eu"
hub-server:  "lobby"
servers:
  lobby: "lobby"
  us: "us"
  eu: "eu"

── Recommended features.yml for Lobby ─────────────────
features:
  rtp: false
  random-respawn: false

── All three servers share the same database.yml ───────
```
