# Upgrading to TeleHop 4

On each stopped backend, move the previous `telehop-paper-*.jar` out of `plugins/` before installing `telehop-bukkit-<version>.jar`. Both files identify the same TeleHop plugin and use the existing `plugins/TeleHop/` data folder. Replace the proxy JAR with the matching Velocity build.

Stop player traffic, back up the shared database and retain each instance's plugin JAR and data folder. Upgrade the Velocity proxies first, then each backend. Complete the upgrade before enabling command variants or depending on V4 permission checks across servers. A production rollback needs the JAR, configuration and database backup from the same deployment.

## Existing installations

Each instance determines its own profile from its existing multi-proxy switch. An explicit `true` selects `MULTI`. A false or absent switch preserves the previous single-proxy behaviour with `SINGLE`. Names of servers and regions play no part in this decision. Use `CUSTOM` for manual control. An invalid switch stops the migration so you can correct it before any files are applied.

| Previous configuration | Result |
| --- | --- |
| Monolithic Paper `config.yml` | Converted into operational settings and a root profile selector. The original is also saved as `config.yml.old`, using a numbered suffix when necessary. The backend's saved spawn goes into `storage.yml`. |
| Split Paper `config/*.yml` | Operational files move to the selected profile. Database credentials stay in `config/database.yml`. In SINGLE, region and messaging sections live in `general.yml`. MULTI and CUSTOM store them in `regions.yml` and `messaging.yml`. |
| `config/spawns.yml` with `hub-command.enabled` | The existing `features.hub` wins. When absent, it inherits the old value. The obsolete key is removed from the active spawn configuration. |
| Paper `config/commands.yml` | Each customised command and alias moves into its group under `commands/`. The root custom-name switch is enabled when a legacy list differs from the bundled defaults. |
| Velocity `config.properties` | Registered backends, proxy identity and timing settings migrate to the selected YAML profile. SINGLE keeps the proxy section in `servers.yml`. MULTI and CUSTOM have a separate `proxy.yml`. Database, Redis authentication, TLS verification and CA settings move to shared YAML. The original properties file, including unrecognised keys, is archived. |
| Edited translations | Existing text remains. Missing keys are filled from the bundled language files. Check placeholder diagnostics if a required field is missing. |

When automatic backups are enabled, recognised flat configuration files are retired after their archive has been written. Existing files in the selected profile take precedence. A pre-existing inactive directory is preserved. The plugin creates missing profile directories with installation defaults. Network IDs, access policies, coordinates and player data are never inferred from examples.

## Recovery

On startup, TeleHop replaces a missing `config/WIKI.md` or an exact copy of a recognised earlier bundled guide with the guide from the installed JAR. An administrator's edited guide stays in place. The original short V4 root selector also gains the setup instructions when it still matches the generated text. Its selected mode and command-name switch are preserved. User-edited selectors retain their contents.

TeleHop stages and validates the candidate before publishing it. The `migrations/state.yml` file records the platform, completed migration identifier and configuration schema. `migrations/history.log` records successful changes. A sealed `migrations/work/` journal is replayed after an interrupted commit. Restart after correcting a disk or permissions error. If an administrator changed a target while a migration was pending, compare the staged, original and live copies before restarting.

Each private directory in `migrations/backups/` contains the original files under `original/`, a `SHA256SUMS` manifest and recovery metadata. On a POSIX filesystem the plugin restricts the backup directories and files to the owner. Protect the entire folder when copying or exporting it. To check a backup on macOS, open a terminal in its `original/` directory and run `shasum -a 256 -c ../SHA256SUMS`.

To restore, stop the affected plugin instance and preserve a copy of its current folder, including `migrations/`. Restore the saved pre-upgrade data folder and matching JAR. If restoring from the migration archive, copy its `original/` contents into a fresh plugin data folder, preserving separately managed files and the matching runtime storage. Restore the database from your consistent network backup if reverting a database schema change. Check command names, one saved destination and an existing player's home before reopening the network.

`migration.backup-retention` defaults to five completed snapshots and accepts 1 to 100. Disabling `migration.backup-before-update` leaves legacy source files in place and omits the permanent archive. Temporary private staging still protects partial writes. [Previous release upgrades](../LEGACY.md)
