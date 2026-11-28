# Upgrading an existing TeleHop network

Use the notes for your installed version and each later section up to your target. The headings describe when a change was introduced. For a direct upgrade to 3.0.1, install the matching 3.0.1 JARs and follow the intervening sections for the settings that changed. [Current installation requirements](README.md#installation) · [Release history](CHANGELOG.md)

## Before the upgrade

1. Record your TeleHop versions, Java versions, backend registrations and proxy IDs. Save copies of both installed JARs, each Paper `plugins/TeleHop/` folder and each Velocity `plugins/telehop-velocity/` folder.
2. Take a consistent backup of the shared database while the network is quiet. For a 1.x upgrade, record the home count and a sample owner's slot, world, coordinates and orientation so you can compare them afterwards.
3. Plan a coordinated restart. Pause player travel and stop the old components before enabling a release that introduces new packet types. Every proxy and backend participating in the new routes should run the matching TeleHop release.
4. On a copy of your installation, start one upgraded instance with the copied database and check the schema and existing data. For the network rollout, give the first instance time to complete its schema work before starting the others.

## From 1.x

Early 1.x installations used a single Paper configuration file. Later 1.x installations can already have a split configuration. The 2.0.0 upgrade notes also cover the change from numbered to named homes. The current schema migrates a legacy `homes` table with primary key `(uuid, slot)` to `(uuid, name)`. Slot 1 becomes `Home 1`, slot 2 becomes `Home 2` and so on. Home names belong to each owner. The backend, world, coordinates and orientation are retained. The legacy `tpa_requests.expiry` column is renamed to `sent_at` when present.

- Keep any original `config.yml` in place for the first start. Where `config/general.yml` does not already exist, Paper migrates the old settings into `config/` and archives the source as `config.yml.old`. An existing backup keeps its name and the new archive receives a numbered suffix. TeleHop preserves existing split files and saved spawn data. If migration stops, correct the file named in the log and restart to resume. Compare your saved file with the generated YAML, particularly server names, database settings, RTP regions and customised features.
- If startup reports duplicate home names, stop and inspect that owner’s rows in the backup and the partially migrated table. Their numbered slots and original primary key are retained. Give conflicting names distinct values with the network stopped, then restart and compare the rows.
- Open `/home` with an existing account and visit a migrated location. Compare the home count and sample row with your backup. The default home permission grants one slot. Grant `telehop.homes.2` through `telehop.homes.10` to the ranks that should have more, subject to the configured limit in `config/home.yml`.
- Preserve `storage.yml` when present. Check the ordinary backend spawn before adding named destinations.

## From 2.0.x

The 2.1.0 release introduced destination ping estimates in the RTP region menu. Velocity sends `SERVER_PING_UPDATE`, a packet added after 2.0.0. Coordinate the proxy and backend updates so that a 2.0.0 receiver cannot receive that newer packet.

- Compare `config/rtp.yml` with the bundled template. The `rtp.gui.ping` section controls the display and its estimated proxy latency. Retain your existing region and dimension definitions.
- If installing 2.1.0 itself, add `rtp-in-progress` to customised language files, including the on-disk English fallback. When moving directly to 3.0.0, the bundled translation supplies missing keys. Add a local value if you want custom wording.
- Open the region menu, select a dimension and complete one trip. Closing and reopening the menu before selecting a destination should leave the cooldown available. This release introduced no database schema change.

## From 2.1.x

The 2.2.0 release moved warmup countdowns into a centred title. Check the existing `show-countdown` setting and the delay for each teleport type. A zero-second delay skips the countdown. Set `rtp.delay-seconds` to `5` if your network wants a five-second RTP warmup.

For 2.2.0 itself, add `countdown-title` to customised language files, including `languages/en.yml`. In 3.0.0, bundled translations supply missing values and local wording takes precedence. Test a positive delay with a member who lacks the bypass permission and confirm that moving cancels a warmup when configured. This release kept the 2.1.0 packet and database formats. [Message examples](docs/messages.md#upgrading-existing-language-files-to-220)

## From 2.2.x

The 2.3.0 release introduced configurable Paper command names. On first start, Paper creates `config/commands.yml` with the existing defaults. Each list begins with the primary name, followed by its aliases. For example, `tp: [networktp, ntp]` gives TeleHop those roots and leaves `/tp` available to another plugin. Restart Paper after editing command names and update any command visibility rules to match.

Existing grants of `telehop.tp` include the coordinate permissions. To restrict one form, use an explicit denial of `telehop.tp.coordinates.self` or `telehop.tp.coordinates.others`. [Command configuration](docs/configuration.md#command-names-and-aliases)

## From 2.3.x to 3.0.0

1. Put `telehop-velocity-3.0.0.jar` on every proxy and `telehop-paper-3.0.0.jar` on every backend. Keep each instance's existing configuration, storage and language files. Review the Java requirements for its platform. [Platform table](README.md#installation)
2. Start the proxies and then the backends. Check that every instance connects to the same database. Paper creates `config/spawns.yml`. New SQL tables hold named destinations, backend defaults and world routes. Each backend publishes its own snapshot at startup and after a successful reload.
3. Before configuring any routes, try bare `/spawn` through each entry proxy. It should reach the existing regional or network hub. Check your normal RTP menu, an existing home and a warp. Run `/telehop version` on each backend and check each proxy's startup log.
4. To add a custom destination, choose a name in the owning backend's YAML, use a loaded world and run `/telehop reload`. Try `/spawn <name>` from another world on that backend and from a different backend. On a multi-proxy network, repeat from each entry proxy. To change bare `/spawn`, enable routing on the **source** backend. To register `/hub`, enable it and restart Paper.
5. Test a restricted destination with a member and with a group granted `telehop.spawn.destination.<name>`. Existing staff parents still grant their children. For limited staff roles, grant the individual action, such as `telehop.admin.homes.list` for inspection or `telehop.admin.homes.visit` for visits. [Spawn configuration and permissions](docs/spawns.md)

## From 3.0.0 to 3.0.1

Install matching 3.0.1 builds. Paper adds missing entries to the existing `commands.yml` and `features.yml` at startup. Review `features.hub`: it inherits your old `spawns.yml` choice unless you already set the new switch. Check your custom command names in `commands.yml`, restart Paper after changing registration, and try the command with a member account. [Hub setup](docs/spawns.md#enable-the-hub-command)

## From 3.0.1 to 4.0.0

Stop the network and save a consistent database and configuration backup. Install both matching builds. Start one backend and proxy first, verify their selected profiles, then start the remaining instances. Use the new group files in `commands/` for future edits. Additional commands require a V4 proxy and V4 destination. [Migration and recovery](docs/migration.md) · [Active configuration](docs/configuration.md)

## Returning to the previous release

Stop the upgraded instances and keep a copy of their logs. Restore the previous JARs, configuration folders and matching database backup together, then start the proxies and backends. A database restore returns player data to the backup time. Check a saved home and the normal spawn route before reopening the network.

TeleHop 2.3.0 rejects unknown entries in `config/commands.yml`, including the newer `hub` entry. When restoring 2.3.0, use its saved command file and check the Paper log for TeleHop's `Startup complete!` message before testing `/spawn`.
