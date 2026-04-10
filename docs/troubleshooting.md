# Troubleshooting

## /spawn does nothing or says "Unknown command"

- Check that `telehop-paper-1.0.0.jar` is in `plugins/` and the server loaded it (look for "TeleHop" in startup log).
- Player needs `telehop.spawn` permission (true by default).

## /spawn doesn't send me to the lobby

- `hub-server` in `config.yml` must **exactly** match the server name in `velocity.toml`.
- Names are case-sensitive: `"lobby"` is not `"Lobby"` or `"spawn-lobby"`.

## /setwarp or /delwarp says "no permission"

- Requires `telehop.admin` permission (default: OP only).
- In LuckPerms: `lp user <name> permission set telehop.admin`

## /pwarp set says "You don't have permission to create warps"

- The player needs a `telehop.warps.<number>` permission to set a warp limit.
- Without one, the limit is 0 (no warps allowed).
- Fix: `lp group default permission set telehop.warps.3`

## Cross-server TPA/warps don't work

1. All servers (Paper + Velocity) must use the **same MySQL database**.
2. `servers.backends` in Velocity config must list every Paper server.
3. `server-name` on each Paper server must match its `velocity.toml` name.
4. Restart Velocity **before** the Paper servers.

## "Player not found" when using /tpa

- The target must be on a server listed in `servers.backends`.
- Both servers must be running TeleHop with the same database.

## RTP keeps failing

- Check `rtp.max-radius` — increase it if the world is small.
- Verify the world name in `rtp.regions.*.world` matches an actual world.
- Make sure the world has generated terrain in the radius area.

## MySQL connection errors

- Verify MySQL is running and reachable from each server.
- If on different machines, the MySQL user must allow remote connections (`'user'@'%'`).
- Test manually: `mysql -u telehop -p -h <host> telehop`

## Tab-completion only shows local players

- Normal for the first few seconds after startup.
- Cross-server player lists sync periodically via Velocity.
- Check that the Velocity plugin is loaded and `servers.backends` is correct.

## Warps not showing in tab-complete

- Warp names load asynchronously from the database on startup.
- If the database connection is slow, completions may be empty briefly.
- Verify the database has warps: `SELECT * FROM warps;`

## Commands show "Unknown or incomplete command"

- This usually means the plugin failed to load. Check the server startup log for TeleHop errors.
- Verify the jar file is present in `plugins/` and not corrupted (re-download if needed).
- If the plugin loaded but commands still fail, try reconnecting — the client needs to refresh its command list after a server restart.
- Check that no other plugin is conflicting with the same command names.

## Features say "This feature is disabled on this server"

- A feature toggle in `config.yml` is set to `false`. Check the `features:` section.
- Use `/telehop reload` after changing toggles — no restart needed.

## `/telehop reload` didn't apply my MySQL changes

`/telehop reload` reloads config values, language files, and warp caches from memory. It does **not** recreate the MySQL connection pool. If you changed `mysql.*` settings (host, port, credentials, database, pool-size), you must do a full server restart for those to take effect.

## RTP cancelled / "you moved"

If `rtp.delay-seconds` is greater than 0, the player must stand still during the warmup countdown. Moving cancels the teleport. Staff with `telehop.rtp.bypassdelay` permission skip the countdown entirely.
