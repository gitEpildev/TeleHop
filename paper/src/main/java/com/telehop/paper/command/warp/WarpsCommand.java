package com.telehop.paper.command.warp;

import com.telehop.paper.platform.PlatformText;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import com.telehop.common.PermissionNodes;
import com.telehop.common.model.WarpRecord;
import com.telehop.paper.NetworkPaperPlugin;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.stream.Collectors;

@CommandAlias("%{cmd-warps}")
public class WarpsCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public WarpsCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Default
    public void execute(Player player) {
        if (!plugin.isFeatureEnabled("warps")) {
            PlatformText.send(player, plugin.msg("feature-disabled"));
            return;
        }
        String joined = plugin.warpService().listCached().stream()
                .filter(warp -> plugin.permissionService().warp(player, warp, plugin.settings().serverName()))
                .map(WarpRecord::name).collect(Collectors.joining(", "));
        if (joined.isEmpty() && !player.hasPermission(PermissionNodes.WARP)) {
            PlatformText.send(player, plugin.msg("no-permission"));
            return;
        }
        PlatformText.send(player, plugin.msg("warp-list-header", Map.of("warps", joined.isBlank() ? "-" : joined)));
    }
}
