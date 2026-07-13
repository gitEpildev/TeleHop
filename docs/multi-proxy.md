# Multi-Proxy Setup Guide

TeleHop supports multiple Velocity proxies sharing the same set of backend servers. This is common in networks with regional proxies (e.g. USA and EU) where each proxy handles players from a specific region but all backends are shared.

## Prerequisites

- TeleHop v1.1.0+ on all Paper backends and Velocity proxies
- A shared MySQL database accessible from all servers
- A Redis instance accessible from all Velocity proxies (can be shared with other plugins)
- All Velocity proxies must have the same backend servers registered in `velocity.toml`

## Architecture

```
                    ┌─────────────┐
                    │    Redis    │
                    └──────┬──────┘
                           │ pub/sub
              ┌────────────┼────────────┐
              │                         │
       ┌──────┴──────┐          ┌──────┴──────┐
       │ Velocity USA│          │ Velocity EU │
       │ proxy-usa-1 │          │ proxy-eu-1  │
       └──────┬──────┘          └──────┬──────┘
              │ plugin msg             │ plugin msg
    ┌─────────┼─────────┐    ┌────────┼─────────┐
    │         │         │    │        │         │
 lobby-usa   usa       eu  lobby-eu  usa       eu
```

Both proxies can route players to any backend. Redis enables cross-proxy communication so TPA, admin TP, and player lists work regardless of which proxy a player is connected to.

## Step-by-Step Setup

### 1. Velocity Configuration

On **each** Velocity proxy, edit `plugins/telehop-velocity/config.properties`:

```properties
# Give each proxy a unique ID
proxy.id=proxy-usa-1

# Enable multi-proxy mode
multi-proxy.enabled=true
multi-proxy.global-player-list=true
multi-proxy.cross-proxy-timeout-ms=15000

# Redis connection (same instance for all proxies)
redis.host=74.208.95.180
redis.port=6379
redis.password=your-redis-password
redis.channel-prefix=telehop

# Optional: encrypt the Redis connection with TLS
redis.ssl=false
redis.ssl-verify=true
redis.ssl-ca-cert=
```

The second proxy would have `proxy.id=proxy-eu-1` but identical Redis settings.

### 2. Paper Configuration

On **each** Paper backend, edit `plugins/TeleHop/config/general.yml`:

```yaml
multi-proxy:
  enabled: true

regions:
  usa:
    servers:
      - usa
      - lobby-usa
    hub: lobby-usa
  eu:
    servers:
      - eu
      - lobby-eu
    hub: lobby-eu
```

This ensures `/spawn` routes players to their regional lobby.

### 3. Home Blocking

To block home-setting on all lobby servers regardless of their name, add to `home.yml`:

```yaml
homes:
  blocked-server-prefixes:
    - "lobby"
```

This matches `lobby-usa`, `lobby-eu`, `lobby-asia`, etc.

### 4. Apply Changes

- **Paper servers**: Run `/telehop reload` on each server (no restart needed)
- **Velocity proxies**: Restart required (do rolling restarts , one at a time)

## How It Works

### Player Tracking

When a player connects or switches servers, their Velocity proxy broadcasts the update to Redis. All other proxies receive this and update their global player map. This means every proxy knows which server every player is on, even players connected through other proxies.

### Cross-Proxy Packet Forwarding

When a packet handler (TPA, admin TP, transfer) can't find the target player on the local proxy, it checks if multi-proxy is enabled and forwards the packet via Redis. The proxy that owns the target player receives the forwarded packet and executes it locally.

### Global Player List

When `global-player-list` is true, player list requests aggregate names from all proxies. The requesting proxy collects its local players, then asks other proxies for their player lists via Redis, waits up to `cross-proxy-timeout-ms`, and merges the results.

## Configuration Reference

### Velocity (`config.properties`)

| Key | Default | Description |
|-----|---------|-------------|
| `proxy.id` | `proxy-1` | Unique ID for this proxy instance |
| `multi-proxy.enabled` | `false` | Master toggle for cross-proxy features |
| `multi-proxy.global-player-list` | `true` | Include remote proxy players in lists |
| `multi-proxy.cross-proxy-timeout-ms` | `15000` | Timeout for cross-proxy requests |
| `redis.host` | `127.0.0.1` | Redis server hostname |
| `redis.port` | `6379` | Redis server port |
| `redis.password` | (empty) | Redis auth password |
| `redis.channel-prefix` | `telehop` | Prefix for Redis pub/sub channels |
| `redis.ssl` | `false` | Connect to Redis over TLS (rediss) |
| `redis.ssl-verify` | `true` | Verify the server certificate and hostname. Disable only for testing |
| `redis.ssl-ca-cert` | (empty) | Path to a PEM CA certificate for self-signed setups. Empty uses the system trust store |

### Paper (`general.yml`)

| Key | Default | Description |
|-----|---------|-------------|
| `multi-proxy.enabled` | `false` | Enables region-aware spawn routing |
| `regions.<name>.servers` | (empty) | List of servers in this region |
| `regions.<name>.hub` | (empty) | Hub/lobby server for this region |

### Paper (`home.yml`)

| Key | Default | Description |
|-----|---------|-------------|
| `homes.blocked-server-prefixes` | `[]` | Block homes on servers matching these prefixes |

## Troubleshooting

### Players can't TPA across proxies

1. Verify both proxies have `multi-proxy.enabled=true`
2. Check that `proxy.id` is unique on each proxy
3. Verify Redis connectivity: check Velocity logs for "RedisCrossProxyBridge started"
4. Ensure both proxies point to the same Redis instance

### /spawn goes to wrong lobby

1. Check that `multi-proxy.enabled: true` is set in Paper's `general.yml`
2. Verify the `regions` mapping includes the current server
3. Make sure server names in `regions` match exactly what's in `velocity.toml`

### Player list only shows local proxy players

1. Ensure `multi-proxy.global-player-list=true` in Velocity config
2. Check `cross-proxy-timeout-ms` isn't too low (default 15000ms is recommended)
3. Look for Redis errors in Velocity logs

### Redis connection errors

1. Verify the Redis host is reachable from the Velocity server
2. Check the password is correct
3. Ensure the Redis port is open in your firewall
4. Check Redis max connections hasn't been exceeded
