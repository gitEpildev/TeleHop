package com.telehop.paper.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import com.telehop.paper.NetworkPaperPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;

@CommandAlias("forcedelwarp")
@CommandPermission("telehop.admin")
public class ForceDelWarpCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public ForceDelWarpCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Default
    @CommandCompletion("@warps|@networkplayers @nothing")
    public void execute(Player player, String arg1, @Optional String arg2) {
        if (arg2 != null && !arg2.isBlank()) {
            deletePlayerWarp(player, arg1, arg2);
        } else {
            deleteAdminWarp(player, arg1);
        }
    }

    private void deleteAdminWarp(Player player, String name) {
        plugin.warpService().find(name).thenAccept(opt -> {
            if (opt.isEmpty()) {
                player.sendMessage(plugin.msg("warp-not-found", Map.of("name", name)));
                return;
            }
            plugin.warpService().delete(name).thenRun(() ->
                    player.sendMessage(plugin.msg("forcedelwarp-admin-deleted", Map.of("name", name))));
        });
    }

    private void deletePlayerWarp(Player player, String targetName, String warpName) {
        @SuppressWarnings("deprecation")
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        String targetUuid = target.getUniqueId().toString();

        plugin.playerWarpService().find(targetUuid, warpName).thenAccept(opt -> {
            if (opt.isEmpty()) {
                player.sendMessage(plugin.msg("pwarp-not-found", Map.of("name", warpName)));
                return;
            }
            plugin.playerWarpService().delete(targetUuid, warpName).thenRun(() ->
                    player.sendMessage(plugin.msg("forcedelwarp-deleted",
                            Map.of("name", warpName, "player", targetName))));
        });
    }
}
