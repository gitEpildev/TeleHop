# TeleHop backend setup

This guide is installed as `plugins/TeleHop/config/WIKI.md` on Paper, CraftBukkit, Spigot and supported Paper forks. Paths below start at `plugins/TeleHop/`. Start with the root [config.yml](../config.yml).

## Choose the deployment

| Your installation | Set `mode` to | First file to open |
| --- | --- | --- |
| One backend, including several Multiverse worlds | `SINGLE` | [Single general settings](single/general.yml) |
| One Velocity proxy, with one or more backends | `SINGLE` | [Single general settings](single/general.yml) |
| Several Velocity proxies sharing backends and Redis | `MULTI` | [Multi-proxy general settings](multi/general.yml), then [regions](multi/regions.yml) |
| You manage the topology switches yourself | `CUSTOM` | [Custom general settings](custom/general.yml), then [messaging](custom/messaging.yml) |

The backend selects its Minecraft and platform adapters automatically. Stop the instance before switching profiles. Each profile retains its own settings. Commands, translations, database credentials and saved spawn storage are shared across profiles on this backend.

## First start

1. Install the `telehop-bukkit-<version>.jar` backend file in the game server's `plugins/` folder and start the server once to generate the files. Stop it to make the initial edits.
2. Create a MySQL or MariaDB database and enter the host, port, database, user and password in [database.yml](database.yml). Every participating backend and proxy uses the same database. A standalone backend also needs a database.
3. Choose the mode above. In that profile's `general.yml`, choose the language and set `server-name` and `hub-server`. Fresh installs use the backend directory name as the initial identity. For standalone use, the two names may match. Behind Velocity, use the exact registered backend names and select your intended hub.
4. For a standalone backend, set `messaging.network-enabled: false` in the active profile. Online-mode backends disable proxy messaging automatically. For a proxy network, install the matching proxy JAR, register the backends in Velocity's `velocity.toml` and set the hub and backend list in the proxy's active `servers.yml`. Allow connections to backend ports only from trusted proxies.
5. For `MULTI`, give each proxy its own `proxy.id` and configure the same Redis service and channel prefix in each proxy's `plugins/telehop-velocity/config/redis.yml`. Set regional hub and backend membership in the backend profile's `regions.yml`. A single proxy can serve several backends using `SINGLE`.
6. Start the instances. Run `/telehop platform info`, `/telehop diagnostics` and `/telehop config validate` on the backend. Stand at the desired default spawn and run `/telehop spawn setdefault`. Test `/spawn` with a member account.

## Files in the backend folder

| File | What you set |
| --- | --- |
| `config.yml` | Active profile, command customisation switches, migration and logging |
| `config/database.yml` | Shared SQL connection and pool size |
| `config/<profile>/general.yml` | Server identity, hub, language, backend labels and audit |
| `config/<profile>/features.yml` | Feature switches, including registration of the optional hub command |
| `config/<profile>/spawns.yml` | Named destinations and the routes used by bare `/spawn` |
| `config/<profile>/teleport.yml` | Countdown, sound and particle effects |
| `config/<profile>/tpa.yml` | Request timeout, cooldown, delay and movement cancellation |
| `config/<profile>/rtp.yml` | Random teleport menus, regions, dimensions, radii and delays |
| `config/<profile>/respawn.yml` | Random death respawn, excluded worlds, beds and anchors |
| `config/<profile>/home.yml` | Home limits, inventory materials, colours and blocked servers |
| `messaging.yml` and `regions.yml` under `config/multi/` or `config/custom/` | Proxy transport, timeouts and regional membership. In `SINGLE`, both sections are in `config/single/general.yml`. |
| `commands/` | One file per command group for primary labels, aliases and supported additional commands |
| `languages/` | English, Dutch, German, Spanish, Chinese and Polish messages |
| `storage.yml` | The default spawn saved on this backend. TeleHop creates it using a loaded world's spawn and updates it when you set a new default. |
| `migrations/` | TeleHop maintains `state.yml`, `history.log`, migration coordination data and private `backups/`. |

Replace `<profile>` with `single`, `multi` or `custom`. The editable profile templates in the backend JAR follow these same paths.

## Named spawns, Multiverse and the hub

To add `/spawn <name>`, go to the destination in the loaded world and run `/telehop spawn set <name> public`. Choose the name yourself. TeleHop writes the coordinates and rotation in the active `spawns.yml`. Use `restricted` for a private destination and grant `telehop.spawn.destination.<name>` to its members. A LuckPerms world context can restrict the grant to a particular world. Test it with accounts that have different grants.

You can also edit the `destinations` mapping in `spawns.yml` and run `/telehop reload config`. Each entry belongs to the backend that hosts that world. Multiple Multiverse worlds on the same backend share the backend identity. Bare `/spawn` keeps its existing hub or route unless you change `routing` on the source backend.

To enable `/hub`, set both `features.hub` and `features.spawn` to `true` in the active `features.yml`, set `hub-server` in `general.yml`, then restart. Players need `telehop.hub`. To change the command name, also enable `commands.custom-names.enabled` in the root and edit [commands/hub.yml](../commands/hub.yml).

## Command names and random teleportation

The twelve groups are `admin.yml`, `back.yml`, `homes.yml`, `hub.yml`, `lastlocation.yml`, `playerinfo.yml`, `player-warps.yml`, `rtp.yml`, `spawn.yml`, `teleport.yml`, `tpa.yml` and `warps.yml`. They all live in `commands/`.

For an additional travel command, enable `commands.additional-commands.enabled` in the root and `additional-commands.enabled` in the relevant group. Use a stable command ID and give it the label and aliases players should type. Command permissions use the ID. [Spawn command settings](../commands/spawn.yml) · [RTP command settings](../commands/rtp.yml)

Define RTP destinations in the active `rtp.yml`. Empty dimension mappings discover loaded worlds, including Multiverse worlds. A direct command can reference your own configured region, dimension or world. For a command that sends a player to another backend, configure the same ID and matching rules at both ends. Use the active profile's `general.yml` to map region labels to Velocity backend names.

## Apply changes and recover

| Change | How to apply it |
| --- | --- |
| Named destinations, routes or reloadable settings | Run `/telehop config validate`, then `/telehop reload config`. |
| Player messages | Run `/telehop reload messages`. |
| Destination or permission rules on already registered commands | Run `/telehop commands validate`, then `/telehop reload commands`. |
| Deployment profile, backend identity, database connection, transport settings, command labels or hub registration | Restart the affected instance. |

When upgrading a stopped backend, remove the previous plugin JAR from `plugins/` before installing its replacement. The existing `plugins/TeleHop/` folder holds your settings. Before upgrading, save each instance's whole plugin folder and a database backup from the same point in time. The migration journal and private backups cover configuration. Keep them with the matching plugin release for recovery. TeleHop refreshes recognised stock copies of this guide on startup and preserves copies that an administrator has edited.
