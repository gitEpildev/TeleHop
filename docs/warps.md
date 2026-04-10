# Warps Guide

TeleHop has two warp systems: **Admin Warps** and **Player Warps**.

---

## Admin Warps

Server-managed warps for important locations (shop, arena, spawn points). Created by staff, usable by everyone.

### Creating an Admin Warp

Stand at the location and run:

```
/setwarp shop
```

This saves the warp with your exact position, rotation, server, and world. Requires `telehop.admin` permission.

### Using an Admin Warp

```
/warp shop
```

If the warp is on a different server, you are automatically transferred via Velocity first.

### Listing Warps

```
/warps
```

Shows all admin warps across all servers.

### Deleting a Warp

```
/delwarp shop
```

Requires `telehop.admin` permission.

---

## Player Warps

Personal warps that any player can create. The number of warps is controlled by permissions.

### Creating a Warp

Stand at the location and run:

```
/pwarp set home
```

You'll see a confirmation with your warp count: `Warp home created. (1/3)`

### Using Your Warp

```
/pwarp home
```

Or list all your warps:

```
/pwarp list
```

### Deleting a Warp

```
/pwarp del home
```

### Making a Warp Public

By default, warps are private (only you can use them). To let others teleport to your warp:

```
/pwarp public home
```

Run it again to make it private.

### Visiting Another Player's Public Warp

```
/pwarp Steve home
```

This only works if Steve has a warp named "home" that is set to public.

### Warp Limits

Your warp limit is controlled by LuckPerms permissions:

- `telehop.warps.3` — you can have up to 3 warps
- `telehop.warps.10` — up to 10 warps
- `telehop.warps.unlimited` — no limit

If you have no `telehop.warps.*` permission, you cannot create warps. Ask your server admin to set up rank-based limits.

When you hit your limit, `/pwarp set` will tell you:

```
You've reached your warp limit (3). Delete a warp or upgrade your rank.
```

### Cross-Server Warps

Player warps work across servers. If your warp is on the EU server and you're on the US server, `/pwarp home` will transfer you to EU first, then teleport you to the warp location.

---

---

## Admin Warp Management

### Listing All Player Warps

```
/listwarps
```

Shows every player warp across all servers, grouped by owner. Requires `telehop.admin`.

### Listing a Specific Player's Warps

```
/listwarps Steve
```

Shows all of Steve's warps with coordinates, server, public/private status.

### Force-Deleting Warps

Force-delete an admin warp:

```
/forcedelwarp shop
```

Force-delete a specific player's warp:

```
/forcedelwarp Steve home
```

Both require `telehop.admin`.

### Admin-Deleting a Player Warp

```
/pwarp admin del Steve home
```

Alternative to `/forcedelwarp Steve home`. Requires `telehop.admin`.

---

## Command Summary

| Command | What it does | Who can use it |
|---------|-------------|----------------|
| `/setwarp <name>` | Create an admin warp | Staff (`telehop.admin`) |
| `/delwarp <name>` | Delete an admin warp | Staff (`telehop.admin`) |
| `/warp <name>` | Go to an admin warp | Everyone (`telehop.warp`) |
| `/warps` | List admin warps | Everyone (`telehop.warp`) |
| `/pwarp set <name>` | Create a personal warp | Everyone (`telehop.pwarp` + limit perm) |
| `/pwarp del <name>` | Delete your warp | Everyone (`telehop.pwarp`) |
| `/pwarp list` | List your warps | Everyone (`telehop.pwarp`) |
| `/pwarp <name>` | Go to your warp | Everyone (`telehop.pwarp`) |
| `/pwarp <player> <name>` | Go to a public warp | Everyone (`telehop.pwarp`) |
| `/pwarp public <name>` | Toggle public/private | Everyone (`telehop.pwarp`) |
| `/pwarp admin del <player> <name>` | Admin-delete any player warp | Staff (`telehop.admin`) |
| `/listwarps` | List all player warps (all servers) | Staff (`telehop.admin`) |
| `/listwarps <player>` | List a player's warps with details | Staff (`telehop.admin`) |
| `/forcedelwarp <name>` | Force-delete an admin warp | Staff (`telehop.admin`) |
| `/forcedelwarp <player> <name>` | Force-delete a player's warp | Staff (`telehop.admin`) |
