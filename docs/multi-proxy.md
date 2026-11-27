# Multi-proxy networks

Choose `MULTI` when multiple Velocity proxies share a set of backends. Install the same TeleHop version on every proxy and backend. The proxies share Redis for player presence and cross-proxy messages. Every instance shares the SQL database containing homes, warps, spawn routes and player records. A single Velocity proxy with several backends uses `SINGLE`. [Installation](setup.md)

## Connect the proxies

1. Register each participating backend under the same name in each proxy's `velocity.toml`. Use the backend's real reachable address on that proxy.
2. Set `mode: MULTI` in each proxy's `plugins/telehop-velocity/config.yml`. In `config/multi/servers.yml`, list the registered names and choose the global hub. For example, a network whose backends are `gate` and `orchard` could use:

   ```yaml
   servers:
     hub: gate
     backends: [gate, orchard]
   ```

3. Give each proxy a distinct `proxy.id` in `config/multi/proxy.yml`. The generated ID is unique on a fresh proxy. Assign a new one if you copied the folder. To include users on other proxies in player lists, keep `proxy.global-player-list: true`.
4. Configure each proxy's `config/database.yml` and `config/redis.yml`. Use the same database, Redis service and channel prefix across this network. Keep separate networks on different prefixes. Restart the proxies after editing. [Redis authentication and TLS](redis.md)

## Configure backend routing

Set `mode: MULTI` in each backend's `plugins/TeleHop/config.yml`. In `config/multi/general.yml`, set its unique `server-name` and the global `hub-server`. Make the corresponding `config/multi/regions.yml` agree across backends. For example:

```yaml
regions:
  countryside:
    servers: [gate, orchard]
    hub: gate
```

Choose your own region and backend names. Regional spawn routing uses the player's **current backend**. Players on the same backend receive the same regional default regardless of the entry proxy. Configure explicit named or per-world routes in the source backend's `config/multi/spawns.yml`. Define named locations on the backend that owns the world. Travel between loaded worlds on that backend is local, including Multiverse worlds.

Bare `/spawn` tries the world's configured override, then the backend's route, then its regional or global hub. The optional `/hub` command always uses `hub-server`. Restart when changing profile, backend identity, messaging or command registration. Run `/telehop config validate` and `/telehop reload config` for reloadable route changes. [Spawn routing](spawns.md)

## Verify cross-proxy travel

Join through two different proxies. Test a player lookup and a TPA between them, then use a named destination on another backend. Verify both the target backend and the final world and coordinates. Confirm the IDs differ between proxies, the global player list is enabled and the Redis subscriber is connected if the remote player is missing. Verify every proxy registers the destination backend if only one entry point can reach it.

New cross-server command variants require matching V4 support on the backends and proxy. Legacy named travel through a 2.3.0 proxy is rejected before the switch. A destination backend rolled back independently may accept the connection but fail the placement, leaving the player at its ordinary arrival point. Roll out matching JARs together and keep pre-upgrade data backups. [Velocity settings](velocity.md) · [Upgrade procedure](migration.md)
