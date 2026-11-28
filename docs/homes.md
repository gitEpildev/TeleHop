# Homes

Players can save named homes up to the allowance their administrator grants. Homes persist in the shared MySQL or MariaDB database and work across backends.

## GUI

Run `/home` or `/homes` to open the chest menu. Both commands also suggest your own saved names and accept a name for direct travel. The default five-row chest displays ten centred beds across two rows. Each page contains at most ten saved homes. The chest header shows your configured title on the left and the current page on the right, starting at Page 1.

Your allowance determines which pages you can open, including empty pages. The gold **Next Page** arrow appears when another page is available. The red barrier shows **Exit** on page one and **Back** on later pages.

| Effective allowance | Pages | What the player sees |
| --- | --- | --- |
| 1 (default) | 1 | One available slot and nine locked slots on a new account. Exit is the only navigation control. |
| 10 | 1 | Ten slots and Exit. |
| 17 | 2 | Seven usable slots on the last page. |
| 20 | 2 | Ten slots on each page. Page two shows only Back. |
| 50 | 5 | Ten slots on each page. Page five shows only Back. |
| Unlimited | As needed | Next Page stays available beyond the saved homes when the profile cap is zero. |

Click an empty bed to save a home there. For example, the first bed on page three saves `Home 21`. If a rank drops from 50 to 10, only page one opens. Homes outside those ten slots are still accessible with `/homes <name>` and reappear in the menu when the allowance increases.

| Slot State | Default Material | Description |
|------------|------------------|-------------|
| **Occupied (set)** | Lime Bed | Shows the home name, server, world, and coordinates. Click to open the manage sub-menu. |
| **Available (empty)** | Red Bed | Click to save a numbered home at your current position, or use `/sethome <name>` to choose a name. |
| **Locked** | Light Blue Bed | Player lacks the permission. Shows "Upgrade to unlock" lore. |

All bed colours are configurable in `home.yml` using any Minecraft bed material name (`LIME_BED`, `RED_BED`, `LIGHT_BLUE_BED`, etc.).

### World & Server Display

The home tooltip shows the server and world dimension with configurable colours:

- **Overworld** - green by default
- **Nether** - red-to-gold gradient by default
- **The End** - purple-to-blue gradient by default

Server names appear in white by default. To give a backend its own display name or colour, add its name under `homes.server-colors` in the active `home.yml`. Dimension colours are set under `homes.world-colors`.

Coordinates can be hidden by setting `show-location: false`.

### Manage Sub-Menu

Clicking an occupied home opens a sub-menu with:

- **Teleport** (green wool) - teleports to the home, cross-server if needed
- **Delete** (red wool) - opens a confirmation prompt

## Named Homes

Homes are identified by a custom name chosen by the player (e.g. `MYCOOLBASE`, `nether_hub`, `farm`).

- Names are case-insensitive: `Base` and `base` are treated as the same home
- Names can contain English letters, digits, spaces and underscores
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
| `/home`, `/homes` | Opens the homes GUI |
| `/home <name>`, `/homes <name>` | Quick-teleport to a named home |
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
| `telehop.homes.2` | - | Access 2 homes |
| `telehop.homes.3` | - | Access 3 homes |
| `telehop.homes.4` | - | Access 4 homes |
| `telehop.homes.5` | - | Access 5 homes |
| `telehop.homes.6` | - | Access 6 homes |
| `telehop.homes.7` | - | Access 7 homes |
| `telehop.homes.8` | - | Access 8 homes |
| `telehop.homes.9` | - | Access 9 homes |
| `telehop.homes.10` | - | Access 10 homes on one page |
| `telehop.homes.<number>` | - | Use a positive whole-number allowance such as 10, 20, 100 or 240 |
| `telehop.homes.unlimited` | - | Create unlimited homes unless the profile sets a cap |
| `telehop.admin.homes.delete` | Granted by `telehop.admin` | Use `/forcedelhome` |

The highest matching `telehop.homes.<N>` permission determines how many homes a player can create. For example, run `lp group builder permission set telehop.homes.240 true` in the server console. Existing homes can still be visited, updated and deleted after a downgrade. The optional `homes.creation-cap` limits creation on that backend. Zero delegates the allowance to permissions. On upgrade, the previous stock ten-slot limit is retired. Existing positive limits below ten are honoured until you set `creation-cap` explicitly.

## Configuration

The active `config/<profile>/home.yml` accepts the following settings.

```yaml
homes:
  creation-cap: 0
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

### Migration from a slot-based installation

The `homes` table is migrated automatically on first startup:

1. A `name` column is added
2. Existing slot-based homes are renamed to "Home 1", "Home 2", etc.
3. The primary key is changed from `(uuid, slot)` to `(uuid, name)`
4. The `slot` column is dropped

Back up the database and verify an existing owner’s migrated locations. [Legacy upgrade checks](../LEGACY.md#from-1x)
