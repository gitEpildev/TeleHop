package com.telehop.paper.command.warp;

import com.telehop.paper.platform.PlatformText;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import com.telehop.common.PermissionNodes;
import com.telehop.paper.NetworkPaperPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;

@CommandAlias("%{cmd-forcedelwarp}")
public class ForceDelWarpCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public ForceDelWarpCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Default
    @CommandCompletion("@warps|@networkplayers @nothing")
    public void execute(Player player, String arg1, @Optional String arg2) {
        String permission = arg2 == null ? PermissionNodes.ADMIN_WARPS_DELETE : PermissionNodes.ADMIN_PLAYERWARPS_DELETE;
        if (!player.hasPermission(permission)) {
            PlatformText.send(player, plugin.msg("no-permission"));
            return;
        }
        if (arg2 != null && !arg2.isBlank()) {
            deletePlayerWarp(player, arg1, arg2);
        } else {
            deleteAdminWarp(player, arg1);
        }
    }

    private void deleteAdminWarp(Player player, String name) {
        plugin.warpService().find(name).thenAcceptAsync(opt -> {
            if (opt.isEmpty()) {
                PlatformText.send(player, plugin.msg("warp-not-found", Map.of("name", name)));
                return;
            }
            plugin.warpService().delete(name).thenRunAsync(() ->
                    PlatformText.send(player, plugin.msg("forcedelwarp-admin-deleted", Map.of("name", name))), plugin::onMainThread);
        }, plugin::onMainThread);
    }

    private void deletePlayerWarp(Player player, String targetName, String warpName) {
        @SuppressWarnings("deprecation")
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        String targetUuid = target.getUniqueId().toString();

        plugin.playerWarpService().find(targetUuid, warpName).thenAcceptAsync(opt -> {
            if (opt.isEmpty()) {
                PlatformText.send(player, plugin.msg("pwarp-not-found", Map.of("name", warpName)));
                return;
            }
            plugin.playerWarpService().delete(targetUuid, warpName).thenRunAsync(() ->
                    PlatformText.send(player, plugin.msg("forcedelwarp-deleted",
                            Map.of("name", warpName, "player", targetName))), plugin::onMainThread);
        }, plugin::onMainThread);
    }
}
