package com.telehop.paper.command.home;

import com.telehop.paper.platform.PlatformText;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import com.telehop.common.PermissionNodes;
import com.telehop.paper.NetworkPaperPlugin;
import org.bukkit.entity.Player;

import java.util.Map;

@CommandAlias("%{cmd-delhome}")
public class DelHomeCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public DelHomeCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Default
    public void execute(Player player, String name) {
        if (!plugin.isFeatureEnabled("homes")) {
            PlatformText.send(player, plugin.msg("feature-disabled"));
            return;
        }
        if (!plugin.permissionService().has(player, PermissionNodes.DELHOME)) {
            PlatformText.send(player, plugin.msg("no-permission"));
            return;
        }

        if (name == null || name.isBlank()) {
            PlatformText.send(player, plugin.msg("home-invalid-name"));
            return;
        }

        String uuid = player.getUniqueId().toString();
        plugin.services().homeService().find(uuid, name).thenAccept(opt -> {
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                if (opt.isEmpty()) {
                    PlatformText.send(player, plugin.msg("home-not-found"));
                    return;
                }
                plugin.services().homeService().delete(uuid, opt.get().name()).thenRun(() ->
                        org.bukkit.Bukkit.getScheduler().runTask(plugin, () ->
                                PlatformText.send(player, plugin.msg("home-deleted", Map.of("name", opt.get().name())))));
            });
        });
    }
}
