# Contributing to TeleHop

TeleHop welcomes clear reports and small, verifiable changes. Include Paper, Velocity, Java and plugin versions, the command used, expected and observed behaviour, and whether the route crosses worlds or backends. Replace player names, private addresses, database credentials and forwarding secrets with examples.

The server-specific spawn request and follow-up about delegated staff permissions came from [@InvisibleT4rget](https://github.com/InvisibleT4rget). The public discussion explains why operators want local spawn defaults on one backend and a shared hub on another. Operator-defined destination names extend that use case. [Issue 1](https://github.com/gitEpildev/TeleHop/issues/1) · [Issue 2](https://github.com/gitEpildev/TeleHop/issues/2)

The `paper/` Maven module produces `telehop-bukkit-<version>.jar` for supported game servers. The `velocity/` module produces the separate proxy JAR. The package checks inspect both files and their checksums, and a live server must report the expected world adapter, travel provider and chat bridge. [Compatibility matrix](docs/version-compatibility.md)

## Working on a change

- Keep shared records, SQL and messaging in `common/`, backend commands in `paper/`, proxy routing in `velocity/`, and Minecraft-specific behaviour in `paper-versions/`.
- Add permission constants and descriptor defaults together. Exercise a member, a delegated moderator and an administrator. Check explicit denials on both sides of a transfer.
- Use the documented YAML layout for backend and proxy settings and translated player text. Update all six language files. Use British English in the English resources, documentation and changelog. Add short comments where ownership, ordering or failure handling needs explanation.
- Include a dated entry for the project version in `CHANGELOG.md`. Use the release guide for matching versions, prose checks and packaged JAR verification.

## Running the checks

Use Java 21 for Maven and configure a Java 25 Maven toolchain for the newer Paper adapter. Run `mvn clean verify`, `npm ci --prefix .github/release`, `python3 -m unittest discover -s .github/release` and `bash .github/tests/bundle.sh 4.0.0`. Run the runtime matrix against disposable MySQL and Redis instances. The two distribution JARs and a test-only command probe are built separately. See [publishing and local checks](docs/releases/publishing.md) and [spawn setup](docs/spawns.md).

The network acceptance fixture lives in `.github/tests/network/`. From the repository root, install its pinned client with `npm ci --prefix .github/tests/network --ignore-scripts`. Use a disposable schema and a completed Paper 1.21.11 smoke directory. The runtime workflow shows the individual Velocity 3 and 4 commands and the mixed-network check. The latter runs both proxy generations at once, tests players on shared backends and exercises partial upgrades using the published 2.3.0 plugins.

For a backend feature, check both the portable Bukkit path and the native Paper adapter. Keep platform APIs out of the shared module. Reproduce runtime checks locally with the Docker runner described in the [release guide](docs/releases/publishing.md#reproduce-validation-locally).
