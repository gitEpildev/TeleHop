package com.telehop.paper.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import com.telehop.common.PermissionNodes;
import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import com.telehop.common.model.WarpRecord;
import com.telehop.paper.NetworkPaperPlugin;
import org.bukkit.entity.Player;

import java.util.Map;

@CommandAlias("warp")
public class WarpCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public WarpCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Default
    @CommandCompletion("@warps")
    public void warp(Player player, String name) {
        if (!plugin.isFeatureEnabled("warps")) {
            player.sendMessage(plugin.msg("feature-disabled"));
            return;
        }
        if (!plugin.permissionService().has(player, PermissionNodes.WARP) &&
                !plugin.permissionService().has(player, PermissionNodes.warpNode(name))) {
            player.sendMessage(plugin.msg("no-permission"));
            return;
        }

        plugin.warpService().find(name).thenAccept(optionalWarp -> {
            if (optionalWarp.isEmpty()) {
                player.sendMessage(plugin.msg("warp-not-found", Map.of("name", name)));
                return;
            }
            WarpRecord warp = optionalWarp.get();
            player.sendMessage(plugin.msg("warp-teleporting", Map.of("name", name)));
            if (warp.server().equalsIgnoreCase(plugin.settings().serverName())) {
                plugin.teleportToWarp(player, warp);
            } else {
                NetworkPacket packet = NetworkPacket.request(PacketType.TRANSFER_PLAYER, plugin.settings().serverName(), "velocity")
                        .put("uuid", player.getUniqueId().toString())
                        .put("targetServer", warp.server())
                        .put("postAction", "WARP")
                        .put("warp", warp.name());
                plugin.messaging().send(packet);
            }
            plugin.auditLogger().log("warp " + name + " by " + player.getName());
        });
    }

}
