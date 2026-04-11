# Agent Development Guidelines: TeleHop

This document defines the architectural constraints and development standards for TeleHop — a cross-server teleportation plugin for **Paper 1.21+ / Velocity 3.3+** networks.

---

## 1. Module Structure & Boundaries

TeleHop is a multi-module Maven project. Module boundaries are strict.

| Module | Role | Constraint |
| :--- | :--- | :--- |
| `common` | Shared code — DB, models, services, messaging | No Paper or Velocity API imports. Platform-agnostic only. |
| `paper` | Paper backend plugin | May import `common` and Paper API. Never import Velocity. |
| `velocity` | Velocity proxy plugin | May import `common` and Velocity API. Never import Paper. |

If code is needed by both `paper` and `velocity`, it goes in `common`.

---

## 2. Package Roles (Paper Module)

| Package | Role | Constraint |
| :--- | :--- | :--- |
| `config` | Settings, migration | Immutable records (`PaperSettings`). No runtime mutation. |
| `service` | Runtime state, business logic | Thread-safe collections. No direct Bukkit API in async paths. |
| `command` | ACF command handlers | Thin — delegate to services. Permission checks use `PermissionNodes` constants. |
| `handler` | Inbound packet dispatch | Switch on `PacketType`, delegate to services. No outbound logic. |
| `gui` | Inventory GUIs | Use Triumph GUI. Always `disableAllInteractions()`. |
| `listener` | Bukkit event listeners | Thin — delegate to services. |
| `messaging` | Plugin channel transport | Handles encode/decode and channel registration. |

---

## 3. Cross-Server Communication Protocol

All inter-server communication uses the `telehop:network` plugin messaging channel. See `docs/protocol.md` for the full specification.

- **Packets** are `NetworkPacket` objects serialised to JSON via `PacketCodec`.
- **Adding a new packet type:**
  1. Add the enum value to `PacketType.java` in `common`.
  2. Handle it in `PacketHandler.java` (Paper) and/or `VelocityPacketHandler.java`.
  3. Document payload keys and direction in `docs/protocol.md`.
- **Routing** goes through Velocity. Paper servers never send directly to each other.
- **Deduplication** is handled by `RequestTracker` using request IDs and a configurable window.

---

## 4. Database & Data Safety

- **Connection pool:** HikariCP, configured in `DatabaseManager`. Pool name: `TeleHop`.
- **Circuit breaker:** All async DB operations go through `DatabaseManager.supplyAsync()` / `runAsync()`, which are protected by `DatabaseCircuitBreaker`. Never bypass this with raw `CompletableFuture.supplyAsync()`.
- **SQL safety:** All queries use `PreparedStatement` with parameter binding. Never concatenate user input into SQL strings.
- **Schema:** DDL lives in `SqlSchema.java`. New tables or column migrations go there.
- **Repository pattern:** `*Repository.java` classes in `common/db/` handle raw JDBC. `*Service.java` classes wrap them with async futures.

```java
// Correct — circuit breaker protected
databaseManager.supplyAsync(() -> repository.find(uuid, slot));

// Wrong — bypasses circuit breaker
CompletableFuture.supplyAsync(() -> repository.find(uuid, slot));
```

---

## 5. Thread Safety

Minecraft servers are single-threaded. All Bukkit API calls must run on the main thread.

- **Async DB calls:** Use `DatabaseManager.supplyAsync()` / `runAsync()`.
- **Return to main thread:** After any async call, schedule back before touching Bukkit API:

```java
homeService.find(uuid, slot).thenAccept(opt ->
    Bukkit.getScheduler().runTask(plugin, () -> {
        // Safe to use Player, Location, World here
    }));
```

- **In-memory state:** Use `ConcurrentHashMap` for thread-safe maps (e.g., `TpaRuntimeManager`, `BackLocationManager`). Do not use `synchronized` blocks.

---

## 6. Configuration

### Paper (split config files)
Config lives in `plugins/TeleHop/config/` as 7 YAML files:

| File | Content |
| :--- | :--- |
| `general.yml` | Server name, hub server, servers list, language, messaging, audit |
| `database.yml` | MySQL connection details and pool size |
| `features.yml` | Feature toggles (spawn, rtp, tpa, warps, homes, back, tpa-toggle) |
| `teleport.yml` | Show-countdown, particle/sound effects per teleport type |
| `tpa.yml` | TPA timeout, cooldown, delay, cancel-on-move |
| `rtp.yml` | RTP cooldown, delay, max-radius, regions, dimensions, GUI |
| `home.yml` | Max slots, GUI, bed materials, blocked servers, world/server colours |

- `PaperSettings` is an **immutable record** loaded once at startup (and on `/telehop reload`).
- Config templates with documentation comments live in `paper/src/main/resources/config/`.
- Legacy `config.yml` is auto-migrated by `ConfigMigrator` on first boot.

### Velocity
Single `config.properties` file. Loaded by `VelocitySettings`.

---

## 7. Commands & Permissions

- Commands use **ACF** (Aikar Command Framework). Register in `Bootstrap.registerCommands()`.
- Permission nodes are constants in `PermissionNodes.java`. Never use raw permission strings.
- When adding a command:
  1. Create the command class in the appropriate `command/` subpackage.
  2. Register it in `Bootstrap.registerCommands()`.
  3. Add it to `plugin.yml` with default permission.
  4. Add the permission to `PermissionNodes.java`.
  5. Add message keys to **all 6 language files** (`en`, `nl`, `de`, `es`, `zh`, `pl`).
  6. Update `docs/commands.md` and `docs/permissions.md`.

---

## 8. Messages & Internationalisation

- All player-facing text uses **MiniMessage** format via `MessageService`.
- Language files: `paper/src/main/resources/languages/{en,nl,de,es,zh,pl}.yml`.
- When adding a new message key, add it to **all 6 files**. English is the fallback.
- Use placeholders: `<player>`, `<target>`, `<slot>`, etc. — replaced via `Map.of(...)`.

---

## 9. Code Style

- **Java 21** — use records, sealed classes, pattern matching, text blocks where appropriate.
- **Javadoc** on all public classes and non-obvious public methods.
- **No redundant comments.** Comments explain *why*, not *what*.
- **Checkstyle** runs on every build (`mvn verify`). Violations fail the build. Rules are in `checkstyle.xml`.
- **Import rules:** No star imports (except `java.util`, `java.io`, `java.sql`, `co.aikar.commands.annotation`). No unused imports.

---

## 10. Testing

- Tests live in `common/src/test/java/`. Use **JUnit 5** + **Mockito**.
- Mock `DatabaseManager` for service tests — never hit a real database in unit tests.
- Every test must assert concrete behaviour, not just execution.
- CI runs `mvn clean verify` (compile + tests + Checkstyle) on every push and PR.

### Test Guidelines
- Use Arrange / Act / Assert structure.
- Descriptive test names (e.g., `failuresAtThresholdOpenCircuit`).
- One logical behaviour per test.
- No timing-dependent tests with tight windows — use generous margins for async/sleep tests.
- Mock external I/O in unit tests.

---

## 11. Development Checklist

When adding a new feature:

- [ ] Place shared code in `common/`, platform code in `paper/` or `velocity/`.
- [ ] Use `PreparedStatement` for all SQL. No string concatenation.
- [ ] Route async DB calls through `DatabaseManager` (circuit breaker protected).
- [ ] Return to main thread before calling Bukkit API after async work.
- [ ] Add permission constants to `PermissionNodes.java`.
- [ ] Add message keys to all 6 language files.
- [ ] Update `plugin.yml`, docs (`commands.md`, `permissions.md`), and `docs/protocol.md` if adding packets.
- [ ] Run `mvn clean verify` — tests pass, Checkstyle clean.
- [ ] Update `README.md` if the feature is user-facing.
