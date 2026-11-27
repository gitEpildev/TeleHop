# Contributing to TeleHop

TeleHop welcomes clear reports and small, verifiable changes. Include the backend implementation, Minecraft and Java versions, the TeleHop version and any Velocity version. Describe the command, expected and observed behaviour, and whether the route crosses worlds or backends. Replace player names, private addresses, database credentials and forwarding secrets with examples.

The server-specific spawn request and follow-up about delegated staff permissions came from [@InvisibleT4rget](https://github.com/InvisibleT4rget). The public discussion explains why operators want local spawn defaults on one backend and a shared hub on another. Operator-defined destination names extend that use case. [Issue 1](https://github.com/gitEpildev/TeleHop/issues/1) · [Issue 2](https://github.com/gitEpildev/TeleHop/issues/2)

The `paper/` Maven module produces `telehop-bukkit-<version>.jar` for supported game servers. The `velocity/` module produces the separate proxy JAR. The package checks inspect both files and their checksums, and a live server must report the expected world adapter, travel provider and chat bridge. [Compatibility matrix](docs/version-compatibility.md)

## Where the code lives

| Component | Start here |
| --- | --- |
| Backend lifecycle, commands and menus | [Plugin entry point](paper/src/main/java/com/telehop/paper/NetworkPaperPlugin.java) and [backend setup](paper/src/main/java/com/telehop/paper/Bootstrap.java) |
| Server implementation and API selection | [Platform detection](paper/src/main/java/com/telehop/paper/platform/BackendPlatform.java) and [versioned world adapters](paper-versions/) |
| Shared database, packet format and services | [Common module](common/src/main/java/com/telehop/common/) |
| Proxy lifecycle and cross-server routing | [Velocity entry point](velocity/src/main/java/com/telehop/velocity/NetworkVelocityPlugin.java) and [proxy setup](velocity/src/main/java/com/telehop/velocity/VelocityBootstrap.java) |
| Backend files copied on first start | [Editable templates](paper/src/main/resources/) and [installed setup guide](paper/src/main/resources/config/WIKI.md) |
| Build and release automation | [GitHub workflows](.github/workflows/), [release checks](.github/release/), [server fixtures](.github/tests/) and [local test runner](scripts/test-network.py) |

The versioned world adapters serve every supported backend. When the server exposes both asynchronous chunk loading and teleportation, TeleHop uses [Paper's native operations](paper-versions/paper.1.21.x/src/main/java/com/telehop/paper/v1_21/PaperWorldOperations.java). On CraftBukkit and Spigot it schedules those operations on the main server thread.

## Working on a change

- Keep platform APIs out of the shared database, packet and service code.
- Add permission constants and descriptor defaults together. Exercise a member, a delegated moderator and an administrator. Check explicit denials on both sides of a transfer.
- Use the documented YAML layout for backend and proxy settings and translated player text. Update all six language files. Use British English in the English resources, documentation and changelog. Add short comments where ownership, ordering or failure handling needs explanation.
- Write the newest dated entry in `CHANGELOG.md` with the changes a server owner will see. For a new version, run `python3 .github/release/prepare.py sync-version` after installing the release checker. Review the generated metadata changes with the changelog. [Release checks](docs/releases/publishing.md#run-a-release)

## Running the checks

Use Java 21 for Maven and configure a Java 25 Maven toolchain for the newer Paper adapter. Run `mvn clean verify`, `npm ci --prefix .github/release`, `python3 -m unittest discover -s .github/release` and `bash .github/tests/bundle.sh`. Run the runtime matrix against disposable MySQL and Redis instances. The two distribution JARs and a test-only command probe are built separately. See [publishing and local checks](docs/releases/publishing.md) and [spawn setup](docs/spawns.md).

The network acceptance fixture lives in `.github/tests/network/`. From the repository root, install its pinned client with `npm ci --prefix .github/tests/network --ignore-scripts`. Use a disposable schema and a completed Paper 1.21.11 smoke directory. The runtime workflow shows the individual Velocity 3 and 4 commands and the mixed-network check. The latter runs both proxy generations at once, tests players on shared backends and exercises partial upgrades using the published 2.3.0 plugins.

For a backend feature, check both the portable Bukkit path and the native Paper adapter. Reproduce runtime checks locally with the Docker runner described in the [release guide](docs/releases/publishing.md#reproduce-validation-locally).
