# Troubleshooting TeleHop

## A command is missing

Check the Paper startup log for the TeleHop version and any configuration or database errors. Confirm the command's primary name and aliases in `config/commands.yml`. Restart Paper after changing registrations. If a command visibility plugin is installed, check its allowlist using the name registered on that backend.

On 3.0.1, set `hub: true` under `features:` in `config/features.yml`, check the `hub` labels in `config/commands.yml` and restart Paper. On 3.0.0 use `hub-command.enabled` in `config/spawns.yml`. The command uses spawn travel and the `telehop.hub` permission. [Hub setup](spawns.md#enable-the-hub-command)

## Spawn sends a player to the wrong place

For bare `/spawn`, check the current world's override and the backend default in the **source** backend's `config/spawns.yml`. If neither applies, check that backend's regional hub and `hub-server` in `config/general.yml`. Match the target name to its registration on each Velocity proxy. Use `/telehop spawn setdefault` on the target backend to change its ordinary spawn.

For `/spawn <name>`, use `/telehop spawn list` to check the owner and access policy. Confirm that the named world is loaded there and that the player has the required permissions in both contexts. For a rejected reload, correct the error in the backend log and reload again. [Spawn routes and permissions](spawns.md)

## Local spawns work but remote travel fails

Check that the Paper and Velocity JARs are from the same TeleHop release. Each backend needs a unique `server-name`, matching the name registered on every proxy. Check `servers.backends` on each proxy and the shared database connection. For cross-proxy player lookups, also check `proxy.id`, Redis connectivity and the channel prefix. [Multi-proxy setup and partial upgrades](multi-proxy.md)

## A player cannot set or use a warp

- `/setwarp` requires `telehop.admin.warps.set` and `/delwarp` requires `telehop.admin.warps.delete`.
- `/pwarp set` requires `telehop.pwarp` and an available personal warp slot. For example, grant `telehop.warps.3` to a group that may keep three personal warps.
- To visit another player's personal warp, the owner must mark it public. The general global-warp permission is `telehop.warp`. A specific global warp can also be granted with `telehop.warp.<name>`.

See the [warp guide](warps.md) and [permissions](permissions.md).

## TPA cannot find a player

Confirm the target is online and their backend appears in `servers.backends`. Player suggestions update periodically. Test from both entry proxies when investigating a multi-proxy network. If the target has disabled requests with `/tpatoggle`, they must enable them to receive a new request.

## Random teleport fails or is cancelled

Check the region mapping, world name and the range of safe terrain in `config/rtp.yml`. The destination world must be loaded. When `rtp.cancel-on-move` is enabled and `rtp.delay-seconds` is positive, moving during warmup cancels the trip. The `telehop.rtp.bypassdelay` permission skips this delay.

## Database settings or legacy migration fail

Check the database name, host, port and account on every instance. The account needs access from the Minecraft server host and privileges to create or upgrade tables. After changing credentials or pool settings, restart the affected instance so it opens a new pool. [Database setup](setup.md#step-1---create-the-mysql-database)

When a legacy YAML migration fails, the startup log identifies the file to correct. Preserve the original config, split files, storage and database backup. Restart to resume and check migrated data before allowing travel. [Legacy upgrade guide](../LEGACY.md)
