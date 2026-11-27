package com.telehop.paper.command.home;

import com.telehop.paper.platform.PlatformText;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Optional;
import com.telehop.common.PermissionNodes;
import com.telehop.paper.NetworkPaperPlugin;
import com.telehop.paper.gui.HomeGui;
import org.bukkit.entity.Player;

@CommandAlias("%{cmd-home}")
public class HomeCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public HomeCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Default
    public void execute(Player player, @Optional String nameArg) {
        if (!plugin.isFeatureEnabled("homes")) {
            PlatformText.send(player, plugin.msg("feature-disabled"));
            return;
        }
        if (!plugin.permissionService().has(player, PermissionNodes.HOMES)) {
            PlatformText.send(player, plugin.msg("no-permission"));
            return;
        }

        if (nameArg != null && !nameArg.isBlank()) {
            quickTeleport(player, nameArg);
            return;
        }

        HomeGui gui = new HomeGui(plugin, plugin.services().homeService());
        gui.open(player);
    }

    private void quickTeleport(Player player, String name) {
        plugin.services().homeService().find(player.getUniqueId().toString(), name).thenAccept(opt -> {
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                if (opt.isEmpty()) {
                    PlatformText.send(player, plugin.msg("home-not-found"));
                    return;
                }
                HomeGui gui = new HomeGui(plugin, plugin.services().homeService());
                gui.teleportToHome(player, opt.get());
            });
        });
    }
}
