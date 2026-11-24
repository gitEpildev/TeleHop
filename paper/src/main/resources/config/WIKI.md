# TeleHop 3.0.0 configuration

Paper reads the files in `plugins/TeleHop/config/` on startup. Run `/telehop reload` on the backend you edited to refresh its reloadable settings. Restart Paper after changing database settings, command names or whether the optional hub command is registered. Each Velocity proxy reads its own `plugins/telehop-velocity/config.properties` at startup.

## Choosing the right file

| File | Settings |
|---|---|
| `general.yml` | This backend's `server-name`, global `hub-server`, server list, language, messaging and regional hubs |
| `database.yml` | MySQL or MariaDB host, database, credentials and pool size |
| `features.yml` | Enable player features on this backend, including homes, warps, TPA, RTP and random respawn |
| `spawns.yml` | Named destinations owned by this backend, source world routes, backend default route and optional hub command |
| `commands.yml` | Primary commands and aliases registered by Paper |
| `teleport.yml` | Countdown titles, particles and sounds per teleport type |
| `tpa.yml` | Request timeout, cooldown, delay and movement cancellation |
| `rtp.yml` | Safe search radius, regions, dimensions, menus, ping display, delay and cooldown |
| `home.yml` | Home slots, menu layout, bed materials, blocked backends and display colours |
| `respawn.yml` | Random respawn world and radius, excluded worlds, beds and anchors |

The backend's saved default spawn is in `plugins/TeleHop/storage.yml`. Use `/telehop spawn setdefault` at the intended position to update it. Operators can edit the language files under `plugins/TeleHop/languages/`. Customisations take precedence over bundled translations.

## Server identity and routing

Set this Paper backend's `server-name` to the registration in each proxy's `velocity.toml`. Put the same database settings on every proxy and backend. For multi-proxy networks, configure a unique `proxy.id`, shared Redis settings and the same backend registrations on every proxy. Paper uses the region containing the current backend to pick the regional hub when multi-proxy routing is enabled. [Network setup](https://github.com/gitEpildev/TeleHop/blob/main/docs/multi-proxy.md)

## Add a spawn in another world

On the Paper server hosting the world, define a name under `destinations` in `spawns.yml`. Replace the following example with a world and safe coordinates from your own server.

```yaml
destinations:
  gardens:
    world: creative
    x: 12.5
    y: 70
    z: -4.5
    yaw: 90
    pitch: 0
    access: public
```

Load the world, run `/telehop reload`, then try `/spawn gardens`. For private access, set `access: restricted` and grant `telehop.spawn.destination.gardens` to the appropriate group. A backend or world default route is configured on the source backend. The shipped configuration leaves `/spawn` on its ordinary regional or network route. [Route examples](https://github.com/gitEpildev/TeleHop/blob/main/docs/spawns.md)

## Change a command name

Each list in `commands.yml` starts with the command's primary name, followed by any aliases. For example, `tp: [networktp, ntp]` registers `/networktp` and `/ntp` after the next Paper restart. If another plugin filters command visibility, update its allowlist to match.

## Grant permissions

Members have the ordinary spawn and named-spawn commands by default. Operators can grant individual staff actions. For example, a moderator with `telehop.admin.homes.list` can inspect homes and needs `telehop.admin.homes.visit` to visit one. A home slot grant such as `telehop.homes.3` or a warp quota such as `telehop.warps.3` can be set per rank. [All commands and permissions](https://github.com/gitEpildev/TeleHop#commands)

## Upgrading

Back up the shared database and each instance's plugin folder. Preserve each backend's storage and install matching TeleHop JARs on the proxies and backends. The legacy guide covers conversion of old homes and YAML, configuration changes introduced by each release, and recovery. [Legacy guide](https://github.com/gitEpildev/TeleHop/blob/main/LEGACY.md) · [Full configuration reference](https://github.com/gitEpildev/TeleHop/blob/main/docs/configuration.md)
