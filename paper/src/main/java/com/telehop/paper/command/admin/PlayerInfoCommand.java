package com.telehop.paper.command.admin;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import com.telehop.common.PermissionNodes;
import com.telehop.paper.NetworkPaperPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@CommandAlias("playerinfo|pinfo")
@CommandPermission(PermissionNodes.ADMIN)
public class PlayerInfoCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public PlayerInfoCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Default
    @CommandCompletion("@networkplayers")
    public void execute(Player sender, String playerName) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        String uuid = target.getUniqueId().toString();

        var homeCountFuture = plugin.services().homeService().countByPlayer(uuid);
        var warpCountFuture = plugin.services().playerWarpService()
                .listByOwner(uuid).thenApply(java.util.List::size);
        var lastLocFuture = plugin.services().lastLocationService().find(uuid);

        CompletableFuture.allOf(homeCountFuture, warpCountFuture, lastLocFuture).thenRun(() -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                int homeCount = homeCountFuture.join();
                int warpCount = warpCountFuture.join();
                var lastLoc = lastLocFuture.join();

                Player onlineTarget = Bukkit.getPlayer(playerName);
                String currentServer = onlineTarget != null ? plugin.settings().serverName() : "offline";

                sender.sendMessage(plugin.msg("playerinfo-header", Map.of("player", playerName)));
                sender.sendMessage(plugin.msg("playerinfo-server", Map.of("server", currentServer)));
                sender.sendMessage(plugin.msg("playerinfo-homes", Map.of("count", String.valueOf(homeCount))));
                sender.sendMessage(plugin.msg("playerinfo-warps", Map.of("count", String.valueOf(warpCount))));

                if (lastLoc.isPresent()) {
                    var loc = lastLoc.get();
                    sender.sendMessage(plugin.msg("playerinfo-lastloc", Map.of(
                            "server", loc.server(),
                            "world", loc.world(),
                            "x", String.valueOf((int) loc.x()),
                            "y", String.valueOf((int) loc.y()),
                            "z", String.valueOf((int) loc.z())
                    )));
                } else {
                    sender.sendMessage(plugin.msg("playerinfo-lastloc-none"));
                }
            });
        });
    }
}
