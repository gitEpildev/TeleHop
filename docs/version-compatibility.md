# Version compatibility

TeleHop V4 targets Minecraft 1.21.x and the 26.1 generation onwards. The game version identifies the world-storage convention. Runtime probes choose the available platform operations. Later releases must pass the compatibility tests before they are listed for download.

| Target | Status | Evidence |
| --- | --- | --- |
| CraftBukkit and Spigot 1.21 and every patch through 1.21.11, Java 21 | VERIFIED | Both upstream distributions were built at each exact version. A real protocol client exercised named travel, safe random travel, clickable staff commands and the home inventory. Each distribution passed two boots. |
| CraftBukkit and Spigot 26.1, 26.1.1 and 26.1.2, Java 25 | VERIFIED | Exact server builds, two boots and player travel on the shared 26.1 wire protocol. |
| CraftBukkit and Spigot 26.2 and 26.3, Java 25 | VERIFIED for server lifecycle and commands | Each distribution passed two boots, configuration validation, database diagnostics and command checks. The installed protocol client has no 26.2 or 26.3 schema. |
| Purpur 1.21.11 build 2568, Java 21 | VERIFIED | Two boots and real player checks for named and random travel, clickable staff commands and the home GUI on the native Paper path. |
| Paper 1.21, 1.21.1 and 1.21.3 through 1.21.11, Java 21 | VERIFIED for lifecycle and commands | Every pinned server passed database, command and restart checks. The player and proxy suites exercised representative builds. |
| Minecraft 1.21.2 client | VERIFIED through Velocity 3.5 and Paper 1.21.3 | The client logged in on protocol 768 and completed a named teleport with exact position and rotation. |
| Minecraft 26.1 client | VERIFIED through Velocity 4.3.1 and Paper 26.1.2 | The client logged in on protocol 775 and completed the same named teleport and placement checks. |
| Paper 26.1.1, 26.1.2, 26.2 and 26.3, Java 25 | VERIFIED for lifecycle and commands | Every pinned server passed the two-boot suite with the same backend JAR. |
| Velocity 3.3 and 3.5 on Java 21, and 4.3.1 on Java 25 | VERIFIED | All three passed the proxy lifecycle suite. The 3.5 and 4.3.1 builds also routed players between backends. |
| Later numeric releases or other compatible forks | UNTESTED | Startup checks the world APIs, Java and scheduler constraints. A successful capability check alone does not certify a new release. |
| Folia | UNSUPPORTED | Verified loader refusal on Folia 1.21.11. |
| Minecraft versions before 1.21 | UNSUPPORTED | V4 starts at 1.21. Its Bukkit descriptor and shared class files require a modern server and Java 21 or later. |

Exact upstream build numbers, game versions, Java versions and SHA-256 values are tracked in the repository. A server boot, a successful client login and a complete mixed-network travel test cover different behaviours. [Compatibility inventory](../.github/release/compatibility.json) · [Runtime tests](../.github/workflows/runtime-tests.yml) · [Backend platforms](platforms.md)

## Upstream releases and publication

Several Spigot patch names resolve to newer source revisions in BuildTools. TeleHop's test inventory uses a numbered upstream revision and an exact BuildData commit for every CraftBukkit and Spigot release. The workflow checks the game version recorded in that commit, builds both server JARs, then checks the game version reported by TeleHop in each process. The locally built server distributions are test fixtures. [Spigot revision 4349](https://hub.spigotmc.org/versions/4349.json) · [Spigot revision 4611](https://hub.spigotmc.org/versions/4611.json)

A Modrinth version applies each selected loader to every game version on that record. The backend release lists additional loaders once they pass the complete published Paper range. The exact 1.21.2 and 26.1 CraftBukkit and Spigot servers have separate upstream pins. The player suite checks the actual login handshake whenever adjacent releases share a protocol. [Compatibility inventory](../.github/release/compatibility.json)

## Version selection

For 1.21.x, the backend writes the world name and also accepts a world key when reading. On 26.1 and later it writes the namespaced key and also checks existing stored world names. The 26.x adapter is compiled with a Java 25 toolchain and emits Java 21 class files so older supported servers can inspect the complete shaded JAR.

Both traditional Bukkit API strings such as `1.21.11-R0.2-SNAPSHOT` and Paper build strings such as `26.3.build.263-stable` are recognised. A Paper build channel can describe an alpha or beta server build for a released game. A Minecraft preview such as `26.2-pre1` has a different game version. The selector reports a startup error for preview, malformed or out-of-range versions. For an unfamiliar fork, retain your backups and check the reported adapter, then test same-world and cross-world placement, command permissions and the home inventory before upgrading a network.
