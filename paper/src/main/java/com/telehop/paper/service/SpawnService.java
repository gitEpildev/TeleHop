package com.telehop.paper.service;

import com.telehop.common.PermissionNodes;
import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import com.telehop.common.model.SpawnEntry;
import com.telehop.common.service.SpawnDirectory;
import com.telehop.paper.NetworkPaperPlugin;
import com.telehop.paper.config.SpawnConfigFile;
import com.telehop.paper.config.StorageManager;
import com.telehop.common.model.BackendSpawns;
import com.telehop.common.model.SpawnPoint;
import com.telehop.paper.config.SpawnRoutes;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Resolves player travel and publishes locations owned by this backend. */
public final class SpawnService {
    private final NetworkPaperPlugin plugin;
    private final SpawnDirectory directory;
    private final StorageManager storage;
    private boolean updating;
    private boolean ready;
    private final Map<UUID, UUID> lookups = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastTravel = new ConcurrentHashMap<>();
    private volatile java.util.List<SpawnEntry> suggestions = java.util.List.of();
    private final java.util.concurrent.atomic.AtomicLong refreshSequence = new java.util.concurrent.atomic.AtomicLong();
    private SpawnRoutes routes;
    private final boolean hubCommand;

    public void forget(UUID player) {
        lastTravel.remove(player);
        lookups.remove(player);
    }

    public SpawnService(NetworkPaperPlugin plugin, SpawnDirectory directory, SpawnRoutes routes, StorageManager storage, boolean hubCommand) {
        this.plugin = plugin;
        this.directory = directory;
        this.routes = routes;
        this.storage = storage;
        this.hubCommand = hubCommand;
    }

    /** Suggestions are advisory. Every actual trip reads the current database revision. */
    public void refreshSuggestions() {
        long sequence = refreshSequence.incrementAndGet();
        directory.list().whenComplete((entries, failure) -> {
            if (sequence == refreshSequence.get()) suggestions = failure == null ? java.util.List.copyOf(entries) : java.util.List.of();
        });
    }

    public java.util.List<String> suggestions(Player player) {
        if (player == null || !player.hasPermission(PermissionNodes.SPAWN_NAMED)) return java.util.List.of();
        return suggestions.stream().filter(entry -> allowed(player, entry, true)).map(SpawnEntry::id).toList();
    }

    /** Activate a configuration only after MySQL commits all of its definitions and routes. */
    public CompletableFuture<Void> apply(String server, SpawnRoutes next, SpawnEntry expected) {
        if (updating) return CompletableFuture.failedFuture(new IllegalStateException("A spawn update is already running"));
        var backend = new BackendSpawns(server, storage.defaultPoint(), next.enabled(), hubCommand,
                next.destinations(), next.allRoutes());
        updating = true;
        return directory.configure(backend, expected)
                .whenCompleteAsync((ignored, error) -> {
                    updating = false;
                    if (error == null) {
                        routes = next;
                        ready = true;
                        refreshSuggestions();
                    }
                }, task -> Bukkit.getScheduler().runTask(plugin, task));
    }

    /** Registration stays fixed until Paper restarts, including when routes are republished. */
    public boolean hubCommand() { return hubCommand; }

    private String local() { return plugin.settings().serverName(); }

    private <T> void result(CommandSender sender, CompletableFuture<T> future, Consumer<T> accept) {
        result(sender, future, accept, () -> true);
    }

    private <T> void lookup(Player player, CompletableFuture<T> future, Consumer<T> accept) {
        UUID id = player.getUniqueId();
        UUID ticket = UUID.randomUUID();
        lookups.put(id, ticket);
        result(player, future, accept, () -> lookups.remove(id, ticket) && player.isOnline());
    }

    private <T> void result(CommandSender sender, CompletableFuture<T> future, Consumer<T> accept, BooleanSupplier current) {
        future.whenComplete((value, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!current.getAsBoolean()) return;
            if (error != null) {
                plugin.getLogger().warning("Spawn operation failed: " + error.getMessage());
                sender.sendMessage(plugin.msg("spawn-unavailable"));
            } else {
                accept.accept(value);
            }
        }));
    }

    private String id(CommandSender sender, String text) {
        try { return SpawnEntry.normalise(text); }
        catch (IllegalArgumentException ex) { sender.sendMessage(plugin.msg("spawn-invalid")); return null; }
    }

    public void travel(Player player, String requested, boolean hub) {
        if (!plugin.isFeatureEnabled("spawn")) {
            player.sendMessage(plugin.msg("feature-disabled"));
            return;
        }
        long now = System.currentTimeMillis();
        Long previous = lastTravel.get(player.getUniqueId());
        if (previous != null && now - previous < 1000) return;
        lastTravel.put(player.getUniqueId(), now);
        lookups.remove(player.getUniqueId());
        if (!player.hasPermission(hub ? PermissionNodes.HUB : PermissionNodes.SPAWN)
                || requested != null && !player.hasPermission(PermissionNodes.SPAWN_NAMED)) {
            player.sendMessage(plugin.msg("no-permission"));
            return;
        }
        var world = player.getWorld();
        var route = hub || requested != null ? null : routes.resolve(world.getKey().toString(), world.getName());
        String target = hub ? plugin.settings().hubServer() : route == null
                ? plugin.settings().getRegionalHub(local()) : route.server();
        String named = requested != null ? requested : route == null ? null : route.id();
        // Regional spawn predates the directory and can run while its first publication is pending.
        if (!ready && (named != null || route != null || hub)) {
            player.sendMessage(plugin.msg("spawn-unavailable"));
            return;
        }
        if (named == null) {
            if (route != null || hub) {
                if (target.equalsIgnoreCase(local())) placeDefault(player);
                else remote(player, target, "", "", false, hub);
                return;
            }
            player.sendMessage(plugin.msg("spawn-sent"));
            if (target.equalsIgnoreCase(local())) {
                plugin.services().teleportService().teleportToSpawn(player);
            } else {
                plugin.messaging().send(player, transfer(player, target).put("postAction", "SPAWN"));
            }
            return;
        }
        String canonical = id(player, named);
        if (canonical == null) return;
        boolean explicit = requested != null;
        lookup(player, directory.find(canonical), found -> {
            if (!world.equals(player.getWorld())) return;
            var entry = found.orElse(null);
            if (entry == null || route != null && !entry.server().equalsIgnoreCase(target)) {
                player.sendMessage(plugin.msg("spawn-unavailable"));
            } else if (!allowed(player, entry, explicit)) {
                player.sendMessage(plugin.msg("no-permission"));
            } else if (entry.server().equalsIgnoreCase(local())) {
                place(player, entry);
            } else {
                remote(player, entry.server(), entry.id(), entry.revision().toString(), explicit, false);
            }
        });
    }

    private void remote(Player player, String target, String name, String revision, boolean explicit, boolean hub) {
        var packet = NetworkPacket.request(PacketType.NAMED_SPAWN_TRANSFER, local(), "velocity")
                .put("uuid", player.getUniqueId().toString()).put("targetServer", target)
                .put("spawn", name).put("revision", revision).put("explicit", String.valueOf(explicit))
                .put("hub", String.valueOf(hub))
                .put("errorText", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(plugin.msg("spawn-unavailable")));
        var world = player.getWorld();
        var probe = NetworkPacket.spawnProbe(local(), "velocity", player.getUniqueId());
        lookup(player, plugin.messaging().sendRequest(player, probe), capability -> {
            if (!world.equals(player.getWorld())) return;
            if (!capability.isSuccess() || !capability.supportsSpawns()) {
                player.sendMessage(plugin.msg("spawn-unavailable"));
                return;
            }
            lookup(player, plugin.messaging().sendRequest(player, packet), reply -> {
                if (!reply.isSuccess()) player.sendMessage(plugin.msg("spawn-unavailable"));
            });
        });
    }

    private void placeDefault(Player player) {
        var location = plugin.services().storageManager().spawn(false);
        if (location == null) { player.sendMessage(plugin.msg("spawn-unavailable")); return; }
        lookup(player, plugin.services().teleportService().teleportToNamedSpawn(player, location), success -> {
            if (!success) player.sendMessage(plugin.msg("spawn-unavailable"));
        });
    }

    private NetworkPacket transfer(Player player, String server) {
        return NetworkPacket.request(PacketType.TRANSFER_PLAYER, local(), "velocity")
                .put("uuid", player.getUniqueId().toString()).put("targetServer", server);
    }

    private boolean allowed(Player player, SpawnEntry entry, boolean explicit) {
        return plugin.isFeatureEnabled("spawn") && player.hasPermission(PermissionNodes.SPAWN)
                && (!explicit || player.hasPermission(PermissionNodes.SPAWN_NAMED))
                && plugin.permissionService().destination(player, PermissionNodes.SPAWN_DESTINATION + entry.id(), entry.restricted());
    }

    public void arrive(Player player, NetworkPacket packet) {
        if (!ready || !local().equalsIgnoreCase(packet.getTargetServer())) {
            player.sendMessage(plugin.msg("spawn-unavailable"));
            return;
        }
        if (packet.get("spawn").isEmpty()) {
            String permission = Boolean.parseBoolean(packet.get("hub")) ? PermissionNodes.HUB : PermissionNodes.SPAWN;
            if (plugin.isFeatureEnabled("spawn") && player.hasPermission(permission)) placeDefault(player);
            else player.sendMessage(plugin.msg("no-permission"));
            return;
        }
        lookup(player, directory.find(packet.get("spawn")), found -> {
            var entry = found.orElse(null);
            if (entry == null || !local().equalsIgnoreCase(entry.server())
                    || !entry.revision().toString().equals(packet.get("revision"))) {
                player.sendMessage(plugin.msg("spawn-unavailable"));
            } else if (!allowed(player, entry, Boolean.parseBoolean(packet.get("explicit")))) {
                player.sendMessage(plugin.msg("no-permission"));
            } else {
                place(player, entry);
            }
        });
    }

    private void place(Player player, SpawnEntry entry) {
        var point = entry.point();
        if (!point.equals(routes.destinations().get(entry.id()))) {
            player.sendMessage(plugin.msg("spawn-unavailable"));
            return;
        }
        var world = plugin.versionAdapter().resolveWorld(point.world());
        var location = world == null ? null : new org.bukkit.Location(world, point.x(), point.y(), point.z(), point.yaw(), point.pitch());
        if (location == null) { player.sendMessage(plugin.msg("spawn-unavailable")); return; }
        lookup(player, plugin.services().teleportService().teleportToNamedSpawn(player, location), success -> {
            player.sendMessage(plugin.msg(success ? "spawn-arrived" : "spawn-unavailable",
                    Map.of("name", entry.id())));
        });
    }

    public void saveDefault(Player player) {
        if (updating) { player.sendMessage(plugin.msg("spawn-conflict")); return; }
        var previous = storage.defaultPoint();
        try { storage.setSpawnLocation(player.getLocation()); }
        catch (IllegalStateException ex) {
            plugin.getLogger().warning(ex.getMessage());
            player.sendMessage(plugin.msg("spawn-unavailable"));
            return;
        }
        apply(local(), routes, null).whenComplete((ignored, failure) -> {
            if (failure == null) player.sendMessage(plugin.msg("spawn-default-saved"));
            else {
                player.sendMessage(plugin.msg("spawn-unavailable"));
                plugin.getLogger().warning("Could not publish the default spawn: " + failure.getMessage());
                storage.setDefaultPoint(previous);
            }
        });
    }

    public void save(Player player, String text, String access) {
        String canonical = id(player, text);
        if (canonical == null) return;
        if (!"public".equalsIgnoreCase(access) && !"restricted".equalsIgnoreCase(access)) {
            player.sendMessage(plugin.msg("spawn-access"));
            return;
        }
        var location = player.getLocation();
        var point = new SpawnPoint(plugin.versionAdapter().getWorldName(location.getWorld()), location.getX(),
                location.getY(), location.getZ(), location.getYaw(), location.getPitch(), "restricted".equalsIgnoreCase(access));
        result(player, directory.find(canonical), found -> {
            if (!player.isOnline() || !player.hasPermission(PermissionNodes.ADMIN_SPAWN_SET)) return;
            if (found.isPresent() && !local().equalsIgnoreCase(found.get().server())) {
                player.sendMessage(plugin.msg("spawn-conflict"));
                return;
            }
            edit(player, canonical, point, found.orElse(null));
        });
    }

    private void edit(CommandSender sender, String name, SpawnPoint point, SpawnEntry expected) {
        if (updating) { sender.sendMessage(plugin.msg("spawn-conflict")); return; }
        final SpawnConfigFile change;
        try {
            change = SpawnConfigFile.edit(plugin.getDataFolder(), name, point);
            if (!change.original().equals(routes)) {
                sender.sendMessage(plugin.msg("spawn-reload-first"));
                return;
            }
            change.write(false);
        } catch (Exception ex) {
            plugin.getLogger().warning("Cannot edit spawns.yml: " + ex.getMessage());
            sender.sendMessage(plugin.msg("spawn-unavailable"));
            return;
        }
        apply(local(), change.configuration(), expected).whenComplete((ignored, error) -> {
            if (error == null) {
                sender.sendMessage(plugin.msg(point == null ? "spawn-deleted" : "spawn-saved", Map.of("name", name)));
            } else {
                // Preserve an operator edit made while the database transaction was running.
                try { change.write(true); }
                catch (IOException ex) { plugin.getLogger().warning("Reload spawns.yml after resolving: " + ex.getMessage()); }
                plugin.getLogger().warning("Spawn update rejected: " + error.getMessage());
                sender.sendMessage(plugin.msg("spawn-conflict"));
            }
        });
    }

    public void delete(CommandSender sender, String text, String confirmation) {
        String canonical = id(sender, text);
        if (canonical == null) return;
        result(sender, directory.find(canonical), found -> {
            if (found.isEmpty() || !local().equalsIgnoreCase(found.get().server())) {
                sender.sendMessage(plugin.msg("spawn-conflict"));
                return;
            }
            var entry = found.get();
            if (!entry.revision().toString().equals(confirmation)) {
                sender.sendMessage(plugin.msg("spawn-confirm", Map.of("name", canonical, "revision", entry.revision().toString())));
                return;
            }
            if (sender.hasPermission(PermissionNodes.ADMIN_SPAWN_DELETE)) edit(sender, canonical, null, entry);
        });
    }

    public void list(CommandSender sender) {
        result(sender, directory.list(), entries -> {
            sender.sendMessage(plugin.msg("spawn-count", Map.of("count", String.valueOf(entries.size()))));
            for (var entry : entries) sender.sendMessage(plugin.msg(entry.restricted() ? "spawn-list-restricted" : "spawn-list-public",
                    Map.of("name", entry.id(), "server", entry.server())));
        });
    }
}
