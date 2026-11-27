# Install TeleHop

Place `telehop-bukkit-<version>.jar` on each supported Paper, CraftBukkit, Spigot or Purpur server. Use `telehop-velocity-<version>.jar` on each proxy. For a standalone server, install only the backend JAR. All participating instances need access to one MySQL or MariaDB database. [Platform and Java compatibility](version-compatibility.md)

You can read the [backend's bundled setup guide](../paper/src/main/resources/config/WIKI.md) before installation. When the plugin starts it installs this guide at `plugins/TeleHop/config/WIKI.md`.

## Step 1 - Create the MySQL Database

Create a database and account. Replace the example host with the permitted Minecraft server host or subnet and choose a private password. Grant access for each machine that will connect.

```sql
CREATE DATABASE telehop CHARACTER SET utf8mb4;
CREATE USER 'telehop'@'localhost' IDENTIFIED BY 'replace-with-a-private-password';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX ON telehop.* TO 'telehop'@'localhost';
```

Back up the database and the plugin folders before upgrading an existing network. [Database settings](database.md) · [Upgrade and recovery](migration.md)

## Step 2 - Choose the deployment

| Deployment | Root `mode` | Install |
| --- | --- | --- |
| One standalone backend, including multiple Multiverse worlds | `SINGLE` | Backend JAR |
| One Velocity proxy with one or more backends | `SINGLE` | Proxy JAR on Velocity and backend JAR on each backend |
| Multiple Velocity proxies sharing backends | `MULTI` | Proxy JAR on each proxy and backend JAR on each backend, plus shared Redis |
| Operator-defined topology | `CUSTOM` | Configure the topology switches in each active profile |

Place each JAR in the instance's `plugins/` directory. Start each instance once to generate the configuration, then stop it. If the default database is unreachable, the initial configuration is still available for editing. Set `mode` in each plugin's root `config.yml`. The lowercase directory `config/single/`, `config/multi/` or `config/custom/` is the active profile.

## Step 3 - Connect the backends

1. In `plugins/TeleHop/config/database.yml`, set the database connection details.
2. In the active `general.yml`, set `server-name` to the exact backend name registered on Velocity and `hub-server` to the desired backend. On a standalone server, use the same local identifier for both. The initial value is derived from the server directory.
3. On an online-mode standalone server, TeleHop disables network plugin messaging automatically. On an **offline-mode standalone** server, explicitly set `messaging.network-enabled: false` in `config/single/general.yml`. Behind Velocity, protect backend ports so only the trusted proxies can connect and configure Velocity's forwarding on each backend.
4. Set `language` in the active `general.yml` independently on each backend. The six supplied files are in `languages/`. To add another locale, copy `en.yml`, translate the text while retaining its placeholders and set the language to the new filename without `.yml`.
5. To enable the optional hub command, set `features.hub: true` and `features.spawn: true` in the active `features.yml`. Command labels and aliases live in `commands/hub.yml`. Registration changes require a restart. [Language and feature settings](configuration.md) · [Hub setup](spawns.md#enable-the-hub-command)

On a standalone server the configured worlds must be loaded. For example, use a world's actual name from Multiverse when defining a named spawn. [Configure named spawns](spawns.md)

## Step 4 - Connect Velocity

1. Register each backend and its network address in Velocity's own `velocity.toml`.
2. In `plugins/telehop-velocity/config/database.yml`, enter the same database as the backends.
3. In the active `servers.yml` set `servers.hub` and list the backend names under `servers.backends`. These names must match Velocity's registrations.
4. For `MULTI`, configure a unique `proxy.id` in each proxy's `config/multi/proxy.yml` and the same Redis service and channel prefix in each proxy's `config/redis.yml`. [Velocity fields](velocity.md) · [Multi-proxy setup](multi-proxy.md)

## Step 5 - Verify the installation

Start the database, Redis if required, the proxies and the backends. Check the TeleHop startup log on each instance. On each backend run `/telehop config validate` and `/telehop commands list`. Use `/telehop spawn setdefault` in game to record that backend's default spawn. Test `/spawn` from each participating backend and, on multi-proxy networks, from each entry proxy.

Travellers need the permissions appropriate to each command. For example, grant `telehop.warps.3` to a group that may create three personal warps. Grant `telehop.warps.unlimited` for unlimited slots. [Command reference](commands.md) · [Permissions and LuckPerms](permissions.md)
