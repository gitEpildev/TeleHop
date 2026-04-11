# Homes

TeleHop's homes system lets players save up to 5 personal teleport locations. Homes persist in MySQL and work cross-server.

## GUI

Running `/home` opens a single-chest GUI (3 rows by default, configurable via `gui-rows`). Home beds are centered in the middle row.

| Slot State | Default Material | Description |
|------------|------------------|-------------|
| **Occupied (set)** | Lime Bed | Shows server, world, and coordinates. Click to open the manage sub-menu. |
| **Available (empty)** | Red Bed | Player has the permission for this slot. Click to set a home. |
| **Locked** | Light Blue Bed | Player lacks the permission. Shows "Upgrade to unlock" lore. |

All bed colours are configurable in `home.yml` using any Minecraft bed material name (`LIME_BED`, `RED_BED`, `LIGHT_BLUE_BED`, etc.).

### World & Server Display

The home tooltip shows the server and world dimension with configurable colours:

- **Overworld** — green by default
- **Nether** — red-to-gold gradient by default
- **The End** — purple-to-blue gradient by default

Server names also display with per-server colours (e.g. Lobby in gray, USA in red, EU in blue). Customise all of these in `home.yml` under `world-colors` and `server-colors`.

Coordinates can be hidden by setting `show-location: false`.

### Manage Sub-Menu

Clicking an occupied home opens a sub-menu with:

- **Teleport** (green wool) — teleports to the home, cross-server if needed
- **Delete** (red wool) — opens a confirmation prompt

### Confirmation

When `confirm-set: true` in `config/home.yml`, setting a home shows a small yes/no GUI first.

## Blocked Servers

Servers listed in `homes.blocked-servers` (e.g. `lobby`) prevent players from setting new homes on those servers. Players can still:

- Open the `/home` GUI from any server
- Teleport to existing homes from any server
- View their homes list

Empty bed slots on blocked servers show "Cannot set homes on this server" in red.

## Commands

| Command | Description |
|---------|-------------|
| `/home` | Opens the homes GUI |
| `/home <1-5>` | Quick-teleport to a specific home slot |
| `/sethome` | Sets a home in the first empty slot (blocked on blocked-servers) |
| `/forcedelhome <player>` | Admin: lists a player's homes with clickable delete buttons |

## Permissions

| Node | Default | Description |
|------|---------|-------------|
| `telehop.homes` | `true` | Use `/home` and `/sethome` |
| `telehop.homes.1` | `true` | Access 1 home slot |
| `telehop.homes.2` | — | Access 2 home slots |
| `telehop.homes.3` | — | Access 3 home slots |
| `telehop.homes.4` | — | Access 4 home slots |
| `telehop.homes.5` | — | Access 5 home slots |
| `telehop.admin` | `op` | Use `/forcedelhome` |

The highest matching `telehop.homes.<N>` permission determines how many slots a player can use. Use LuckPerms to assign higher slot counts to ranks.

## Configuration

Full reference in `config/home.yml`:

```yaml
homes:
  max-slots: 5
  confirm-set: true
  gui-title: "<gradient:red:gold>Your Homes</gradient>"
  gui-rows: 3

  # Bed materials per slot state
  bed-set: "LIME_BED"
  bed-empty: "RED_BED"
  bed-locked: "LIGHT_BLUE_BED"

  # Prevent setting homes on these servers
  blocked-servers:
    - "lobby"

  # Show x/y/z coordinates in the home tooltip
  show-location: true

  # Dimension display names (MiniMessage format)
  world-colors:
    overworld: "<green>Overworld</green>"
    nether: "<gradient:red:gold>Nether</gradient>"
    the-end: "<gradient:dark_purple:blue>The End</gradient>"

  # Per-server display names (MiniMessage format)
  server-colors:
    lobby: "<gray>Lobby"
    usa: "<red>USA"
    eu: "<blue>EU"
```

## Cross-Server

When a player teleports to a home on a different server, TeleHop sends a `TRANSFER_PLAYER` packet with `postAction=HOME` through Velocity. The player is transferred to the target server and teleported to the home coordinates on arrival.

## Database

Homes are stored in the `homes` table:

```sql
CREATE TABLE IF NOT EXISTS homes (
  uuid    VARCHAR(36) NOT NULL,
  slot    INT         NOT NULL,
  server  VARCHAR(64) NOT NULL,
  world   VARCHAR(64) NOT NULL,
  x       DOUBLE      NOT NULL,
  y       DOUBLE      NOT NULL,
  z       DOUBLE      NOT NULL,
  yaw     FLOAT       NOT NULL,
  pitch   FLOAT       NOT NULL,
  PRIMARY KEY (uuid, slot)
);
```
