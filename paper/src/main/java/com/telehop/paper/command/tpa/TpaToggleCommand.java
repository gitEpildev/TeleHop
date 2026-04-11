package com.telehop.paper.command.tpa;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import com.telehop.common.PermissionNodes;
import com.telehop.paper.NetworkPaperPlugin;
import org.bukkit.entity.Player;

@CommandAlias("tpatoggle")
public class TpaToggleCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public TpaToggleCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Default
    public void execute(Player player) {
        if (!plugin.isFeatureEnabled("tpa-toggle")) {
            player.sendMessage(plugin.msg("feature-disabled"));
            return;
        }
        if (!plugin.permissionService().has(player, PermissionNodes.TPA_TOGGLE)) {
            player.sendMessage(plugin.msg("no-permission"));
            return;
        }

        boolean nowDisabled = plugin.tpaRuntimeManager().toggleTpa(player.getUniqueId());
        if (nowDisabled) {
            player.sendMessage(plugin.msg("tpa-toggle-off"));
        } else {
            player.sendMessage(plugin.msg("tpa-toggle-on"));
        }
    }
}
