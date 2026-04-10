package com.telehop.paper.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import com.telehop.common.model.PlayerWarpRecord;
import com.telehop.paper.NetworkPaperPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

@CommandAlias("listwarps")
@CommandPermission("telehop.admin")
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
        plugin.playerWarpService().listAll().thenAccept(warps -> {
            if (warps.isEmpty()) {
                player.sendMessage(plugin.msg("listwarps-none"));
                return;
            }

            Map<String, List<PlayerWarpRecord>> grouped = warps.stream()
                    .collect(Collectors.groupingBy(PlayerWarpRecord::ownerUuid, LinkedHashMap::new, Collectors.toList()));

            player.sendMessage(plugin.msg("listwarps-header", java.util.Map.of("count", String.valueOf(warps.size()))));
            for (var entry : grouped.entrySet()) {
                String ownerName = resolvePlayerName(entry.getKey());
                List<PlayerWarpRecord> playerWarps = entry.getValue();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < playerWarps.size(); i++) {
                    if (i > 0) sb.append("<gray>, ");
                    PlayerWarpRecord w = playerWarps.get(i);
                    sb.append("<aqua>").append(w.name());
                    if (w.isPublic()) sb.append(" <dark_gray>[public]");
                    sb.append("<gray>@").append(w.server());
                }
                player.sendMessage(plugin.mm("<yellow>" + ownerName + " <gray>(" + playerWarps.size() + "): " + sb));
            }
        });
    }

    private void listForPlayer(Player player, String targetName) {
        @SuppressWarnings("deprecation")
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        String targetUuid = target.getUniqueId().toString();

        plugin.playerWarpService().listByOwner(targetUuid).thenAccept(warps -> {
            if (warps.isEmpty()) {
                player.sendMessage(plugin.msg("listwarps-player-none", java.util.Map.of("player", targetName)));
                return;
            }

            player.sendMessage(plugin.msg("listwarps-player-header", java.util.Map.of("player", targetName, "count", String.valueOf(warps.size()))));
            for (PlayerWarpRecord w : warps) {
                String pub = w.isPublic() ? "<green>[public]" : "<red>[private]";
                player.sendMessage(plugin.mm("<gray>  <aqua>" + w.name() + " " + pub +
                        " <dark_gray>— " + w.server() + " " + w.world() +
                        " (" + (int) w.x() + ", " + (int) w.y() + ", " + (int) w.z() + ")"));
            }
        });
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
