package com.telehop.paper.command.warp;

import com.telehop.paper.platform.PlatformText;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import com.telehop.common.model.PlayerWarpRecord;
import com.telehop.common.PermissionNodes;
import com.telehop.paper.NetworkPaperPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

@CommandAlias("%{cmd-listwarps}")
@CommandPermission(PermissionNodes.ADMIN_WARPS_LIST)
public class ListWarpsCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public ListWarpsCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Default
    @CommandCompletion("@networkplayers")
    public void execute(Player player, @co.aikar.commands.annotation.Optional String targetName) {
        if (targetName != null && !targetName.isBlank()) {
            listForPlayer(player, targetName);
        } else {
            listAll(player);
        }
    }

    private void listAll(Player player) {
        plugin.playerWarpService().listAll().thenAcceptAsync(warps -> {
            if (!player.isOnline() || !player.hasPermission(PermissionNodes.ADMIN_WARPS_LIST)) return;
            if (warps.isEmpty()) {
                PlatformText.send(player, plugin.msg("listwarps-none"));
                return;
            }

            Map<String, List<PlayerWarpRecord>> grouped = warps.stream()
                    .collect(Collectors.groupingBy(PlayerWarpRecord::ownerUuid, LinkedHashMap::new, Collectors.toList()));

            PlatformText.send(player, plugin.msg("listwarps-header", java.util.Map.of("count", String.valueOf(warps.size()))));
            for (var entry : grouped.entrySet()) {
                String ownerName = resolvePlayerName(entry.getKey());
                List<PlayerWarpRecord> playerWarps = entry.getValue();
                var messages = plugin.messageService();
                var items = playerWarps.stream().map(warp -> messages.rawFormat("listwarps-summary-item",
                        Map.of("name", warp.name(), "server", warp.server()),
                        Map.of("visibility", warp.isPublic() ? Component.space().append(messages.raw("warp-public-label")) : Component.empty()))).toList();
                var list = Component.join(JoinConfiguration.separator(Component.text(", ")), items);
                PlatformText.send(player, messages.rawFormat("listwarps-owner-line", Map.of("player", ownerName,
                        "count", String.valueOf(playerWarps.size())), Map.of("warps", list)));
            }
        }, task -> Bukkit.getScheduler().runTask(plugin, task));
    }

    private void listForPlayer(Player player, String targetName) {
        @SuppressWarnings("deprecation")
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        String targetUuid = target.getUniqueId().toString();

        plugin.playerWarpService().listByOwner(targetUuid).thenAcceptAsync(warps -> {
            if (!player.isOnline() || !player.hasPermission(PermissionNodes.ADMIN_WARPS_LIST)) return;
            if (warps.isEmpty()) {
                PlatformText.send(player, plugin.msg("listwarps-player-none", java.util.Map.of("player", targetName)));
                return;
            }

            PlatformText.send(player, plugin.msg("listwarps-player-header", java.util.Map.of("player", targetName, "count", String.valueOf(warps.size()))));
            for (PlayerWarpRecord w : warps) {
                var messages = plugin.messageService();
                PlatformText.send(player, messages.rawFormat("listwarps-detail", Map.of("name", w.name(),
                        "server", w.server(), "world", w.world(), "x", String.valueOf((int) w.x()),
                        "y", String.valueOf((int) w.y()), "z", String.valueOf((int) w.z())),
                        Map.of("visibility", messages.raw(w.isPublic() ? "warp-public-label" : "warp-private-label"))));
            }
        }, task -> Bukkit.getScheduler().runTask(plugin, task));
    }

    private String resolvePlayerName(String uuid) {
        try {
            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
            String name = op.getName();
            return name != null ? name : uuid.substring(0, 8);
        } catch (Exception e) {
            return uuid.substring(0, 8);
        }
    }
}
