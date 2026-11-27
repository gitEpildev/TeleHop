package com.telehop.paper.command;

import co.aikar.commands.BukkitCommandManager;
import com.telehop.common.PermissionNodes;
import com.telehop.common.service.PlayerWarpService;
import com.telehop.paper.service.PersonalWarpSuggestions;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import com.telehop.paper.NetworkPaperPlugin;
import com.telehop.paper.config.CommandNames;
import com.telehop.paper.service.VariantTravel;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;

/** Checks names before ACF registration and keeps private travel commands out of clients' suggestions. */
public final class TravelCommandRegistry implements Listener {
    private final NetworkPaperPlugin plugin;

    private final PersonalWarpSuggestions personal;
    private TravelCommandRegistry(NetworkPaperPlugin plugin, PlayerWarpService warps) {
        this.plugin = plugin;
        personal = new PersonalWarpSuggestions(warps);
    }

    public static void checkOwnership(NetworkPaperPlugin plugin, CommandNames commands) {
        // CraftBukkit and Paper expose their command map at this boundary, unlike the baseline Bukkit interface.
        CommandMap map;
        try {
            var server = plugin.getServer();
            map = (CommandMap) server.getClass().getMethod("getCommandMap").invoke(server);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException("Cannot inspect server command ownership before registration", ex);
        }
        checkOwnership(plugin, commands, map);
    }

    static void checkOwnership(NetworkPaperPlugin plugin, CommandNames commands, CommandMap map) {
        commands.labels().forEach((id, labels) -> {
            for (String label : labels) {
                var other = map.getCommand(label);
                if (other == null) continue;
                if (other instanceof PluginIdentifiableCommand owned && owned.getPlugin() == plugin) continue;
                // The historic /tp root intentionally shadows vanilla's /tp. minecraft:tp remains available.
                if (id.equals("tp") && label.equals("tp") && (map.getCommand("minecraft:tp") == other
                        || other.getClass().getName().contains("VanillaCommand"))) continue;
                String owner = other instanceof PluginIdentifiableCommand owned ? owned.getPlugin().getName() : other.getName();
                throw new IllegalArgumentException("Command /" + label + " for " + id + " is already registered by " + owner
                        + ". Change its name in commands/ and restart.");
            }
        });
    }

    public static void register(NetworkPaperPlugin plugin, BukkitCommandManager manager, PlayerWarpService warps) {
        var registry = new TravelCommandRegistry(plugin, warps);
        manager.getCommandCompletions().registerCompletion("playerwarps", context -> {
            var player = context.getPlayer();
            return player != null && plugin.isFeatureEnabled("player-warps") && player.hasPermission(PermissionNodes.PWARP)
                    ? registry.personal.list(player.getUniqueId()) : java.util.List.of();
        });
        plugin.commandNames().variants().forEach((id, variant) -> variant.labels().forEach(label ->
                manager.registerCommand(new TravelVariantCommand(label, id, plugin))));
        plugin.getServer().getPluginManager().registerEvents(registry, plugin);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.isFeatureEnabled("player-warps") && event.getPlayer().hasPermission(PermissionNodes.PWARP))
            personal.list(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { personal.forget(event.getPlayer().getUniqueId()); }

    @EventHandler
    public void visibleCommands(PlayerCommandSendEvent event) {
        for (var variant : plugin.commandNames().variants().values()) {
            if (VariantTravel.visible(plugin, event.getPlayer(), variant)) continue;
            for (String label : variant.labels()) {
                event.getCommands().remove(label);
                event.getCommands().remove("telehop:" + label);
            }
        }
    }
}
