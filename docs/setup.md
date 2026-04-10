# Setup Guide

## Requirements

| Software   | Minimum Version | Notes                       |
|------------|----------------|-----------------------------|
| Paper      | 1.21+          | Folia is not supported      |
| Velocity   | 3.3+           | BungeeCord is not supported |
| Java       | 21+            |                             |
| MySQL      | 8.0+           | MariaDB 10.5+ also works   |
| LuckPerms  | 5.4+           | Optional                    |

All Paper backends and the Velocity proxy **must connect to the same MySQL database**.

---

## Step 1 — Create the MySQL Database

```sql
CREATE DATABASE telehop;
CREATE USER 'telehop'@'%' IDENTIFIED BY 'your-secure-password';
GRANT SELECT, INSERT, UPDATE, DELETE ON telehop.* TO 'telehop'@'%';
FLUSH PRIVILEGES;
```

Use `'telehop'@'localhost'` instead of `'%'` if all servers are on the same machine.

---

## Step 2 — Install on the Velocity Proxy

1. Place `telehop-velocity-1.0.0.jar` in `plugins/`.
2. Start the proxy. A config is created at `plugins/telehop-velocity/config.properties`.
3. Edit the config with your MySQL credentials and server list:

```properties
mysql.host=127.0.0.1
mysql.port=3306
mysql.database=telehop
mysql.username=telehop
mysql.password=your-secure-password
mysql.poolSize=5

servers.hub=lobby
servers.backends=lobby,eu,usa
```

Server names must exactly match your `velocity.toml` `[servers]` entries.

4. Restart the proxy.

---

## Step 3 — Install on Each Paper Server

1. Place `telehop-paper-1.0.0.jar` in `plugins/`.
2. Start the server. A config is created at `plugins/TeleHop/config.yml`.
3. Edit the config — **`server-name` must be different on each server**:

```yaml
server-name: "usa"        # Must match velocity.toml
hub-server: "lobby"

servers:
  lobby: "lobby"
  eu: "eu"
  us: "usa"

mysql:
  host: "127.0.0.1"
  port: 3306
  database: "telehop"
  username: "telehop"
  password: "your-secure-password"
  pool-size: 5
```

4. Restart each server.

---

## Step 4 — Restart Order

1. Velocity proxy (first)
2. All Paper backends (after Velocity is fully started)

---

## Language Setup

TeleHop supports 6 languages out of the box: English (`en`), Dutch (`nl`), German (`de`), Spanish (`es`), Chinese (`zh`), and Polish (`pl`).

Language files are stored in `plugins/TeleHop/languages/` and are automatically created on first startup. You can edit them freely or create your own (e.g. `fr.yml`). Missing keys in any language automatically fall back to English.

### Single-Server Setup

Set your language in `config.yml` and restart (or use `/telehop reload`):

```yaml
language: "de"   # All players see German
```

### Multi-Server Setup

Each Paper server has its own `config.yml`, so each can use a different language:

| Server | `config.yml` setting |
|--------|---------------------|
| EU     | `language: "de"`    |
| US     | `language: "en"`    |
| Lobby  | `language: "en"`    |

The Velocity proxy has no language setting -- it doesn't send player-facing messages.

### Adding a Custom Language

1. Copy `plugins/TeleHop/languages/en.yml` to a new file (e.g. `fr.yml`).
2. Translate all the values (keep the keys and MiniMessage tags intact).
3. Set `language: "fr"` in `config.yml`.
4. Run `/telehop reload` or restart.

---

## LuckPerms Setup for Player Warp Limits

To give players a warp limit, assign `telehop.warps.<number>` per rank:

```bash
# Default rank gets 3 warps
lp group default permission set telehop.warps.3

# VIP gets 10
lp group vip permission set telehop.warps.10

# Staff gets unlimited
lp group staff permission set telehop.warps.unlimited
```

Players with no `telehop.warps.*` permission cannot create warps.
