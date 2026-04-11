package com.telehop.paper.command.warp;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import com.telehop.common.PermissionNodes;
import com.telehop.common.model.WarpRecord;
import com.telehop.paper.NetworkPaperPlugin;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.stream.Collectors;

@CommandAlias("warps")
public class WarpsCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public WarpsCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Default
    public void execute(Player player) {
        if (!plugin.isFeatureEnabled("warps")) {
            player.sendMessage(plugin.msg("feature-disabled"));
            return;
        }
        if (!plugin.permissionService().has(player, PermissionNodes.WARP)) {
            player.sendMessage(plugin.msg("no-permission"));
            return;
        }
        String joined = plugin.warpService().listCached().stream()
                .map(WarpRecord::name).collect(Collectors.joining(", "));
        player.sendMessage(plugin.msg("warp-list-header", Map.of("warps", joined.isBlank() ? "-" : joined)));
    }
}
