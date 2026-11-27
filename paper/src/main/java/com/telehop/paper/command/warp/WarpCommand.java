package com.telehop.paper.command.warp;

import com.telehop.paper.platform.PlatformText;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import com.telehop.common.model.WarpRecord;
import com.telehop.paper.NetworkPaperPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;

@CommandAlias("%{cmd-warp}")
public class WarpCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public WarpCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Default
    @CommandCompletion("@warps")
    public void warp(Player player, String name) {
        if (!plugin.isFeatureEnabled("warps")) {
            PlatformText.send(player, plugin.msg("feature-disabled"));
            return;
        }
        if (!plugin.permissionService().warp(player, name)) {
            PlatformText.send(player, plugin.msg("no-permission"));
            return;
        }

        plugin.warpService().find(name).thenAccept(optionalWarp -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (optionalWarp.isEmpty()) {
                PlatformText.send(player, plugin.msg("warp-not-found", Map.of("name", name)));
                return;
            }
            if (!plugin.permissionService().warp(player, optionalWarp.get(), plugin.settings().serverName()) || !plugin.isFeatureEnabled("warps")) {
                PlatformText.send(player, plugin.msg("no-permission"));
                return;
            }
            WarpRecord warp = optionalWarp.get();
            PlatformText.send(player, plugin.msg("warp-teleporting", Map.of("name", name)));
            if (warp.server().equalsIgnoreCase(plugin.settings().serverName())) {
                plugin.services().teleportService().teleportToWarp(player, warp);
            } else {
                NetworkPacket packet = NetworkPacket.request(PacketType.TRANSFER_PLAYER, plugin.settings().serverName(), "velocity")
                        .put("uuid", player.getUniqueId().toString())
                        .put("targetServer", warp.server())
                        .put("postAction", "WARP")
                        .put("warp", warp.name());
                plugin.messaging().send(player, packet);
            }
            plugin.auditLogger().log("warp " + name + " by " + player.getName());
        }));
    }

}
