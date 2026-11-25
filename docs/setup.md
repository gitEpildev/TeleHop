# Setup Guide

## Requirements

Install the Paper JAR on each backend and the Velocity JAR from the same TeleHop release on each proxy. Check the Java version required for each instance. [Supported platforms and Java versions](../README.md#installation)

All instances connect to the same MySQL 8.0+ or MariaDB 10.5+ database. LuckPerms is optional. Multiple proxies can share player presence through Redis. [Multi-proxy configuration](multi-proxy.md)

## Step 1 - Create the MySQL Database

```sql
CREATE DATABASE telehop;
CREATE USER 'telehop'@'%' IDENTIFIED BY 'your-secure-password';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER ON telehop.* TO 'telehop'@'%';
FLUSH PRIVILEGES;
```

Use `'telehop'@'localhost'` instead of `'%'` if all servers are on the same machine.

---

## Step 2 - Install on the Velocity Proxy

1. Place `telehop-velocity-3.0.1.jar` in `plugins/`.
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

## Step 3 - Install on Each Paper Server

1. Place `telehop-paper-3.0.1.jar` in `plugins/`.
2. Start the server once to create the split files in `plugins/TeleHop/config/`. Keep `plugins/TeleHop/storage.yml` for the backend's saved default spawn.
3. In `config/general.yml`, set a unique backend name matching its registration on each Velocity proxy.

```yaml
server-name: "usa"
hub-server: "lobby"
servers:
  lobby: "lobby"
  eu: "eu"
  us: "usa"
```

4. In `config/database.yml`, enter the shared database details.

```yaml
mysql:
  host: "127.0.0.1"
  port: 3306
  database: "telehop"
  username: "telehop"
  password: "your-secure-password"
  pool-size: 5
```

5. Restart each server. For an existing installation, check the steps for your installed version. [Legacy upgrade guide](../LEGACY.md)

---

## Step 4 - Restart Order

1. Velocity proxy (first)
2. All Paper backends (after Velocity is fully started)

---

## Language Setup

TeleHop supports 6 languages out of the box: English (`en`), Dutch (`nl`), German (`de`), Spanish (`es`), Chinese (`zh`), and Polish (`pl`).

Language files are stored in `plugins/TeleHop/languages/` and are automatically created on first startup. You can edit them freely or create your own (e.g. `fr.yml`). For a missing key, TeleHop checks the bundled translation for the selected language and then English.

### Single-Server Setup

Set your language in `config/general.yml` and restart (or use `/telehop reload`):

```yaml
language: "de"   # All players see German
```

### Multi-Server Setup

Each Paper server has its own `config/general.yml`, so each can use a different language:

| Server | `config/general.yml` setting |
|--------|---------------------|
| EU     | `language: "de"`    |
| US     | `language: "en"`    |
| Lobby  | `language: "en"`    |

The Velocity proxy has no language setting -- it doesn't send player-facing messages.

### Adding a Custom Language

1. Copy `plugins/TeleHop/languages/en.yml` to a new file (e.g. `fr.yml`).
2. Translate all the values (keep the keys and MiniMessage tags intact).
3. Set `language: "fr"` in `config/general.yml`.
4. Run `/telehop reload` or restart.

---

## Multi-Proxy Setup

If you run multiple Velocity proxies (e.g. regional proxies for USA and EU), see the dedicated **[Multi-Proxy Setup Guide](multi-proxy.md)** for full instructions.

In short, you need to:
1. Set `multi-proxy.enabled=true` and a unique `proxy.id` on each Velocity proxy
2. Configure Redis connection on each proxy
3. Set `multi-proxy.enabled: true` and configure `regions` on each Paper server
4. Restart Velocity proxies; `/telehop reload` on Paper servers

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

## Optional hub command

Set `hub: true` under `features:` in `plugins/TeleHop/config/features.yml` on each Paper backend where players should use it. Keep spawn travel enabled and set the desired command names under `hub` in `config/commands.yml`. Restart Paper and check `/telehop help` for the registered name. The global destination comes from `hub-server` in `general.yml`. [Configuration and upgrade example](spawns.md#enable-the-hub-command)
