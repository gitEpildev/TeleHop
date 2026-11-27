# Backend platforms

Install the same `telehop-bukkit-<version>.jar` in the `plugins` directory of each supported game server. Install `telehop-velocity-<version>.jar` on each Velocity proxy in a network. A standalone backend needs its database and its selected configuration profile. [Setup](setup.md) · [Configuration](configuration.md)

| Implementation | Verified scope | Backend behaviour |
| --- | --- | --- |
| CraftBukkit | Exact checked versions and player coverage are listed in the version matrix | Uses the Bukkit scheduler for chunks and teleports. Interactive messages use the server's vanilla component format. Plain messages use the Bukkit chat API. |
| Spigot | Exact checked versions and player coverage are listed in the version matrix | Uses the Bukkit scheduler and Spigot component chat. |
| Paper | VERIFIED on the builds in the version matrix | Prepares chunks and teleports asynchronously where both native methods are available. |
| Purpur 1.21.11 build 2568 | Two boots, database access, named travel, safe random teleportation, clickable chat and the home inventory | Uses Paper's native operations. |
| Other Bukkit and Paper forks | UNTESTED | The plugin inspects the available methods at startup. Confirm the startup diagnostics and test your backup on an isolated server. |
| Folia | UNSUPPORTED | Folia 1.21.11 build 14 was tested. The server refused to load the JAR before configuration, database access or command registration. |

Bukkit is an API. The CraftBukkit test exercises the implementation built from the original Bukkit API, including interactive chat on an API without Spigot's component method. The mixed-backend test connects a client to Velocity, then visits a named destination on Spigot, Paper and CraftBukkit. It checks coordinates and rotation on each server. Standalone player checks also exercise safe random placement inside an offset world border. The 1.21.11 CraftBukkit check follows a real death and respawn back onto a prepared surface.

## Detection and travel

With the default `logging.platform-detection: true`, startup prints the selected implementation and adapters. Run `/telehop platform info` to read the same details again. For example, on Spigot 1.21.11 the diagnostic reported Minecraft 1.21.11, Java 21, `Paper121Adapter`, Bukkit main-thread travel and Spigot component chat. The adapter name identifies the Minecraft world lookup code.

All supported game servers generate the backend layout in `plugins/TeleHop/`. A Velocity proxy generates its own layout in `plugins/telehop-velocity/`. A new installation starts with the SINGLE profile. Operators select MULTI for several proxies or CUSTOM for their own topology. The platform detector runs independently on every instance. [Backend setup](configuration.md) · [Proxy setup](velocity.md)

The startup log reports the implementation, game and Bukkit API versions, Java runtime, world-name adapter, teleport and chunk provider, and message bridge. On a fork that supplies some Paper APIs, TeleHop checks for both required asynchronous methods before selecting the native operations.

The main backend compiles against Spigot API. A separate class in the existing Paper adapter module contains the calls to native asynchronous methods. TeleHop packages MiniMessage and its Adventure text dependencies privately. Chat, titles, inventory names and lore therefore work on servers without native Adventure.

RTP and random respawn sample columns inside the configured radius and the world border. They inspect blocks on the Bukkit server thread after preparing the chunk. The fallback schedules chunk preparation through Bukkit. The native implementation can load chunks asynchronously. Both stop after a bounded number of attempts.

## Folia

TeleHop's command callbacks, warmups, inventories, cross-player operations and database completions still require a shared server thread. Folia assigns entities and locations to independent region threads. The presence of an entity or region scheduler method on a Paper fork is insufficient to establish safe ownership. Keep the packaged descriptor intact. [Folia plugin support](https://docs.papermc.io/paper/dev/folia-support/)

## Reproducing the platform checks

The backend platform workflow builds each CraftBukkit and Spigot release from its numeric revision with upstream BuildTools build 201. It verifies the BuildData commit and game version before starting either distribution. It checks the pinned SHA-256 for BuildTools, Purpur and Folia. The game servers and Minecraft test clients bind to loopback during these checks. The detailed evidence for each supported game version is in the version matrix. [Platform workflow](../.github/workflows/backend-platforms.yml) · [Version compatibility](version-compatibility.md)
