package com.telehop.paper.command.home;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import com.telehop.common.PermissionNodes;
import com.telehop.paper.NetworkPaperPlugin;
import org.bukkit.entity.Player;

import java.util.Map;

@CommandAlias("delhome")
public class DelHomeCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public DelHomeCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Default
    public void execute(Player player, String name) {
        if (!plugin.isFeatureEnabled("homes")) {
            player.sendMessage(plugin.msg("feature-disabled"));
            return;
        }
        if (!plugin.permissionService().has(player, PermissionNodes.DELHOME)) {
            player.sendMessage(plugin.msg("no-permission"));
            return;
        }

        if (name == null || name.isBlank()) {
            player.sendMessage(plugin.msg("home-invalid-name"));
            return;
        }

        String uuid = player.getUniqueId().toString();
        plugin.services().homeService().find(uuid, name).thenAccept(opt -> {
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                if (opt.isEmpty()) {
                    player.sendMessage(plugin.msg("home-not-found"));
                    return;
                }
                plugin.services().homeService().delete(uuid, opt.get().name()).thenRun(() ->
                        org.bukkit.Bukkit.getScheduler().runTask(plugin, () ->
                                player.sendMessage(plugin.msg("home-deleted", Map.of("name", opt.get().name())))));
            });
        });
    }
}
