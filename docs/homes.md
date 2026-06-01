# Homes

TeleHop's homes system lets players save up to 10 named personal teleport locations. Homes persist in MySQL and work cross-server.

## GUI

Running `/home` opens a large chest GUI (5 rows by default, configurable via `gui-rows`). Home beds are spread across two rows for up to 10 homes.

| Slot State | Default Material | Description |
|------------|------------------|-------------|
| **Occupied (set)** | Lime Bed | Shows the home name, server, world, and coordinates. Click to open the manage sub-menu. |
| **Available (empty)** | Red Bed | Player has permission for this slot. Use `/sethome <name>` to set a home. |
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

When `confirm-set: true` in `config/home.yml`, setting a home via the GUI shows a small yes/no GUI first.

## Named Homes

Homes are identified by a custom name chosen by the player (e.g. `MYCOOLBASE`, `nether_hub`, `farm`).

- Names are case-insensitive: `Base` and `base` are treated as the same home
- Names must be alphanumeric with underscores only (no spaces or special characters)
- Maximum name length: 32 characters
- Overwriting: if you `/sethome` with an existing name, the location is updated

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
| `/home <name>` | Quick-teleport to a named home |
| `/sethome <name>` | Sets a named home at your current location |
| `/delhome <name>` | Deletes a named home |
| `/forcedelhome <player>` | Admin: lists a player's homes with clickable delete buttons |

## Permissions

| Node | Default | Description |
|------|---------|-------------|
| `telehop.homes` | `true` | Use `/home` (open GUI, teleport) |
| `telehop.sethome` | `true` | Use `/sethome` |
| `telehop.delhome` | `true` | Use `/delhome` |
| `telehop.homes.1` | `true` | Access 1 home |
| `telehop.homes.2` | — | Access 2 homes |
| `telehop.homes.3` | — | Access 3 homes |
| `telehop.homes.4` | — | Access 4 homes |
| `telehop.homes.5` | — | Access 5 homes |
| `telehop.homes.6` | — | Access 6 homes |
| `telehop.homes.7` | — | Access 7 homes |
| `telehop.homes.8` | — | Access 8 homes |
| `telehop.homes.9` | — | Access 9 homes |
| `telehop.homes.10` | — | Access 10 homes |
| `telehop.admin` | `op` | Use `/forcedelhome` |

The highest matching `telehop.homes.<N>` permission determines how many homes a player can create. Use LuckPerms to assign higher counts to ranks.

## Configuration

Full reference in `config/home.yml`:

```yaml
homes:
  max-slots: 10
  confirm-set: true
  gui-title: "<gradient:red:gold>Your Homes</gradient>"
  gui-rows: 5

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
  uuid    VARCHAR(36)  NOT NULL,
  name    VARCHAR(32)  NOT NULL,
  server  VARCHAR(64)  NOT NULL,
  world   VARCHAR(64)  NOT NULL,
  x       DOUBLE       NOT NULL,
  y       DOUBLE       NOT NULL,
  z       DOUBLE       NOT NULL,
  yaw     FLOAT        NOT NULL,
  pitch   FLOAT        NOT NULL,
  PRIMARY KEY (uuid, name)
);
```

### Migration from 1.1.0

The `homes` table is migrated automatically on first startup:

1. A `name` column is added
2. Existing slot-based homes are renamed to "Home 1", "Home 2", etc.
3. The primary key is changed from `(uuid, slot)` to `(uuid, name)`
4. The `slot` column is dropped

No manual intervention is needed.
