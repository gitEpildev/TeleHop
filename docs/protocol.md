# Plugin Messaging Protocol

TeleHop uses a custom plugin messaging protocol over the Velocity proxy channel for all cross-server communication.

## Channel

```
telehop:network
```

Registered on both Paper (outgoing + incoming) and Velocity (incoming from all servers).

## Wire Format

Every message is a **JSON-encoded `NetworkPacket`**, serialised to UTF-8 bytes via Gson.

```
[UTF-8 JSON bytes] → PacketCodec.encode() / PacketCodec.decode()
```

### NetworkPacket Structure

| Field | Type | Description |
|-------|------|-------------|
| `requestId` | `UUID` | Unique identifier for this packet; used for request/response correlation and deduplication |
| `type` | `PacketType` | The operation this packet represents (see table below) |
| `originServer` | `String` | Server name that created this packet |
| `targetServer` | `String` | Intended destination server (or `"velocity"` for proxy-handled packets) |
| `timestamp` | `long` | Epoch milliseconds when the packet was created |
| `response` | `boolean` | `true` if this packet is a response to a previous request |
| `success` | `boolean` | For responses — whether the operation succeeded |
| `errorMessage` | `String` | For failed responses — human-readable error description |
| `payload` | `Map<String, String>` | Arbitrary key-value data specific to each packet type |

## Request / Response Flow

```
Paper Server A                    Velocity Proxy                    Paper Server B
     │                                │                                  │
     │──── request (type=X) ─────────▶│                                  │
     │                                │──── routed request ─────────────▶│
     │                                │                                  │
     │                                │◀──── response (type=RESPONSE) ───│
     │◀──── response ─────────────────│                                  │
```

- Requests targeting another Paper server are routed through Velocity.
- Requests targeting `"velocity"` are handled directly by the proxy.
- The `RequestTracker` on each side tracks pending futures by `requestId` with a configurable timeout (default 10 seconds).
- Duplicate requests within the deduplication window (default 30 seconds) are silently dropped.

## Packet Types

### Core Infrastructure

| Type | Direction | Payload Keys | Description |
|------|-----------|-------------|-------------|
| `PING` | Any | — | Health check (reserved) |
| `RESPONSE` | Return | — | Generic response to a prior request |
| `ERROR` | Return | — | Error response |

### Player Tracking

| Type | Direction | Payload Keys | Description |
|------|-----------|-------------|-------------|
| `PLAYER_SERVER_UPDATE` | Paper → Velocity | `uuid`, `server` | Notifies the proxy that a player is on a specific server |
| `PLAYER_LIST_REQUEST` | Paper → Velocity | — | Requests a list of all online players |
| `PLAYER_LIST_RESPONSE` | Velocity → Paper | `names` (CSV) | Comma-separated list of all online player names |

### Server Transfer

| Type | Direction | Payload Keys | Description |
|------|-----------|-------------|-------------|
| `TRANSFER_PLAYER` | Paper → Velocity | `uuid`, `targetServer`, `postAction`, plus action-specific keys | Transfers a player to another server, optionally queuing a post-transfer action |

#### Post-Transfer Actions (via `postAction` key)

When `TRANSFER_PLAYER` includes a `postAction`, Velocity queues a `PendingAction` and executes it once the player connects to the target server:

| postAction | Additional Payload | Effect on Arrival |
|------------|-------------------|-------------------|
| `SPAWN` | — | Teleports to server spawn |
| `WARP` | `warp` | Teleports to the named warp |
| `RTP` | `region`, `dimension` | Executes random teleport |
| `TELEPORT_TO_PLAYER` | `actorUuid`, `targetUuid` | Teleports actor to target player |
| `PWARP` | `pwarpOwner`, `pwarpName` | Teleports to a player warp |
| `HOME` | `homeSlot`, `homeUuid` | Teleports to a home slot |
| `BACK` | `world`, `x`, `y`, `z`, `yaw`, `pitch` | Teleports to stored back location |

### TPA (Teleport Ask)

| Type | Direction | Payload Keys | Description |
|------|-----------|-------------|-------------|
| `TPA_CREATE` | Paper → Velocity → Paper | `senderUuid`, `targetUuid`, `senderName`, `type` (`TPA`/`TPA_HERE`), `sentAt` | Creates a TPA request on the target's server |
| `TPA_ACCEPT` | Paper → Velocity | `senderUuid`, `targetUuid`, `type` | Accepts a pending TPA request; Velocity handles cross-server transfer |
| `TPA_DENY` | Paper → Velocity → Paper | `senderUuid` | Denies a request; routed back to sender's server |
| `TPA_CANCEL` | Paper → Velocity → Paper | `targetUuid` | Cancels a request; routed to target's server |
| `TPA_TIMEOUT` | Internal | `senderUuid`, `targetUuid` | Marks a request as expired (handled by scheduled task) |
| `TPA_TOGGLE_DENY` | Paper → Velocity → Paper | `senderUuid`, `senderTargetName` | Sent when target has TPA disabled; routed back to sender to display denial message |

### Warps

| Type | Direction | Payload Keys | Description |
|------|-----------|-------------|-------------|
| `WARP_TELEPORT` | Velocity → Paper | `uuid`, `warp` | Teleports a player to a warp on the local server |
| `WARP_CREATE` | Paper → Velocity | `name`, `server`, `world`, `x`, `y`, `z`, `yaw`, `pitch` | Creates/updates a warp |
| `WARP_DELETE` | Paper → Velocity | `name` | Deletes a warp |
| `WARP_LIST` | Paper → Velocity | — | Requests the full warp list |

### Player Warps

| Type | Direction | Payload Keys | Description |
|------|-----------|-------------|-------------|
| `PWARP_TELEPORT` | Velocity → Paper | `uuid`, `pwarpOwner`, `pwarpName` | Teleports a player to a player-owned warp |

### Teleportation

| Type | Direction | Payload Keys | Description |
|------|-----------|-------------|-------------|
| `SPAWN_REQUEST` | Velocity → Paper | `uuid` | Teleports a player to spawn on the local server |
| `RTP_REQUEST` | Velocity → Paper | `uuid`, `region`, `dimension` | Executes a random teleport on the local server |
| `TELEPORT_TO_PLAYER` | Velocity → Paper | `actorUuid`, `targetUuid` | Teleports one player to another on the same server |
| `EXECUTE_POST_JOIN_TELEPORT` | Velocity → Paper | `uuid`, `world`, `x`, `y`, `z`, `yaw`, `pitch` | Teleports a player to exact coordinates after server transfer |
| `ADMIN_TP_REQUEST` | Paper → Velocity | `mode` (`SELF_TO_TARGET`, `TARGET_TO_SENDER`, `PLAYER_TO_PLAYER`), player identifiers | Admin teleport routing |

### Homes

| Type | Direction | Payload Keys | Description |
|------|-----------|-------------|-------------|
| `HOME_TELEPORT` | Velocity → Paper | `uuid`, `homeSlot`, `homeUuid` | Teleports player to their home after cross-server transfer |

### Back

| Type | Direction | Payload Keys | Description |
|------|-----------|-------------|-------------|
| `BACK_TELEPORT` | Velocity → Paper | `uuid`, `world`, `x`, `y`, `z`, `yaw`, `pitch` | Teleports player to their back location after cross-server transfer |

## Routing Logic (Velocity Side)

Velocity's `VelocityPacketHandler` routes packets using:

1. **By target name** (`targetName` key): looks up the player on the proxy, resolves their current server.
2. **By target UUID** (`targetUuid`/`senderUuid` key): resolves the server via `PlayerTracker` (database-backed cache).
3. **Direct server name** (`targetServer` field): sends directly to the named server.

### Retry Mechanism

For post-transfer packets that fail to deliver (target server not yet registered as connected), the handler retries up to **8 times** with a **500ms delay** between attempts. This accounts for the brief window between Velocity connection and Paper channel registration.

## Deduplication

The `RequestTracker` on each endpoint maintains a seen-request map keyed by `requestId`. Requests received within the deduplication window (configurable, default 30 seconds) are silently dropped to prevent double-processing during retries or network hiccups.

## Constants

Defined in `com.telehop.common.NetworkConstants`:

| Constant | Value | Description |
|----------|-------|-------------|
| `CHANNEL_ID` | `telehop:network` | Plugin messaging channel identifier |
| `DEFAULT_REQUEST_TIMEOUT_MS` | `10,000` | Default timeout for request/response futures |

## Backward Compatibility

The protocol is currently unversioned (v1 implicit). All packet types and payloads are additive — new types can be introduced without breaking older endpoints, as unknown types are silently ignored by both Paper and Velocity handlers.

Future versions should add a `protocolVersion` field to `NetworkPacket` if breaking changes are required.
