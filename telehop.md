# TeleHop

TeleHop brings homes, warps, player requests, random teleportation and shared spawns to Paper servers behind Velocity. Players can use the same commands across worlds and backends. A network with several entry proxies can share its player directory through Redis.

## New in 3.0.0

You choose the names players can use with `/spawn <name>`. A destination belongs to one Paper backend and records a world, position, facing direction and access policy. On a backend with Multiverse worlds, players can travel between those worlds locally. When the destination belongs to another backend, Velocity connects the player and Paper places them at the saved position.

Bare `/spawn` keeps the existing regional or network hub route with the supplied configuration. Operators can override it per world or backend, or enable a separate `/hub` command. Restricted destinations and individual staff actions support LuckPerms. [Spawn configuration and examples](https://github.com/gitEpildev/TeleHop/blob/main/docs/spawns.md)

## What players can do

| Commands | Use |
|---|---|
| `/spawn`, `/spawn <name>`, optional `/hub` | Visit the default spawn, a named destination or the global hub |
| `/home`, `/sethome <name>`, `/delhome <name>` | Manage personal homes in chat or through the homes menu |
| `/rtp` | Select a region and dimension for a random safe landing |
| `/tpa`, `/tpahere`, `/tpaaccept`, `/tpadeny`, `/tpacancel`, `/tpatoggle` | Send, answer and manage teleport requests |
| `/warps`, `/warp <name>`, `/pwarp` | Use network warps, manage personal warps and visit public player warps |
| `/lastlocation`, `/back`, `/back death` | Return to a logout, teleport or death location when granted access |

Staff can grant separate rights to view, visit, set or delete other players' homes and to teleport themselves or other players. Command names and aliases are configurable. The README lists every default command, alias and permission. [Command reference](https://github.com/gitEpildev/TeleHop#commands) · [Permissions](https://github.com/gitEpildev/TeleHop/blob/main/docs/permissions.md)

## Install

Place the Paper JAR on each backend and the Velocity JAR from the same TeleHop release on each proxy. Paper selects its Minecraft adapter automatically. One proxy JAR handles the supported Velocity 3 and 4 builds.

Use a shared MySQL or MariaDB database and give each backend a unique name matching its Velocity registration. For multi-proxy networks, register the same backends on each proxy, assign unique proxy IDs and configure the shared Redis service. Paper loads its settings from `plugins/TeleHop/config/` and includes six languages with customisable messages. [Supported platforms and Java versions](https://github.com/gitEpildev/TeleHop#installation) · [Setup](https://github.com/gitEpildev/TeleHop/blob/main/docs/setup.md) · [Multi-proxy setup](https://github.com/gitEpildev/TeleHop/blob/main/docs/multi-proxy.md)

## Updates and support

Existing installations should back up their database and plugin folders, then follow the checks for their installed version. [Legacy upgrade guide](https://github.com/gitEpildev/TeleHop/blob/main/LEGACY.md) · [Changelog](https://github.com/gitEpildev/TeleHop/blob/main/CHANGELOG.md) · [Downloads](https://modrinth.com/plugin/telehop/versions)

Report a problem with your Paper, Velocity, Java and TeleHop versions, the command you ran and whether the route crosses a world, backend or proxy. [GitHub issues](https://github.com/gitEpildev/TeleHop/issues) · [Support Discord](https://discord.gg/E8kpxdGbxn)

Developed by Epildev at Epildevconnect Ltd, registered in England and Wales under company number 17247566. Distributed under the MIT License with Additional Terms. [Author](https://github.com/GitEpildev) · [Website](https://developer.epildevconnect.uk/) · [Licence](https://github.com/gitEpildev/TeleHop/blob/main/LICENSE)
