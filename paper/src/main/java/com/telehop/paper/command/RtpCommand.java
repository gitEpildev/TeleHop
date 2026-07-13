package com.telehop.paper.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import com.telehop.common.PermissionNodes;
import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import com.telehop.paper.NetworkPaperPlugin;
import com.telehop.paper.gui.RtpGui;
import com.telehop.paper.service.WarmupTask;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;

public class RtpCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public RtpCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @CommandAlias("rtp")
    @Default
    public void execute(Player player) {
        if (!plugin.isFeatureEnabled("rtp")) {
            player.sendMessage(plugin.msg("feature-disabled"));
            return;
        }
        if (!plugin.permissionService().has(player, PermissionNodes.RTP)) {
            player.sendMessage(plugin.msg("no-permission"));
            return;
        }
        if (!plugin.permissionService().has(player, PermissionNodes.RTP_BYPASS_COOLDOWN)
                && plugin.rtpManager().onCooldown(player)) {
            int remaining = plugin.rtpManager().remainingCooldown(player);
            player.sendMessage(plugin.msg("rtp-cooldown", Map.of("seconds", String.valueOf(remaining))));
            return;
        }
        if (plugin.rtpManager().hasPendingRtp(player)) {
            player.sendMessage(plugin.msg("rtp-in-progress"));
            return;
        }
        player.sendMessage(plugin.msg("rtp-opening"));
        new RtpGui(plugin, (region, dimension) -> onSelection(player, region, dimension)).openRegion(player);
    }

    /**
     * Runs when the player picks a region and dimension in the GUI. The GUI
     * has already closed itself at this point. Cooldown is consumed here, at
     * selection time, so opening and closing the menu never burns it, and the
     * pending guard stops any second selection from overriding the warmup.
     */
    private void onSelection(Player player, String region, String dimension) {
        if (plugin.rtpManager().hasPendingRtp(player)) {
            player.sendMessage(plugin.msg("rtp-in-progress"));
            return;
        }
        if (!plugin.permissionService().has(player, PermissionNodes.RTP_BYPASS_COOLDOWN)
                && plugin.rtpManager().onCooldown(player)) {
            int remaining = plugin.rtpManager().remainingCooldown(player);
            player.sendMessage(plugin.msg("rtp-cooldown", Map.of("seconds", String.valueOf(remaining))));
            return;
        }

        int delay = plugin.settings().rtpDelaySeconds();
        boolean bypass = plugin.permissionService().has(player, PermissionNodes.RTP_BYPASS_DELAY);
        int effectiveDelay = (delay > 0 && !bypass) ? delay : 0;

        plugin.rtpManager().markPendingRtp(player, effectiveDelay);
        plugin.rtpManager().markCooldown(player, plugin.settings().rtpCooldownSeconds());

        if (effectiveDelay > 0) {
            player.sendMessage(plugin.msg("rtp-delay", Map.of("seconds", String.valueOf(effectiveDelay))));
            new WarmupTask(plugin, player, effectiveDelay,
                    plugin.settings().rtpCancelOnMove(),
                    plugin.settings().showCountdown(),
                    () -> {
                        plugin.rtpManager().clearPendingRtp(player);
                        startRtp(player, region, dimension);
                    },
                    () -> {
                        plugin.rtpManager().clearPendingRtp(player);
                        player.sendMessage(plugin.msg("rtp-cancelled"));
                    }
            ).start();
        } else {
            plugin.rtpManager().clearPendingRtp(player);
            startRtp(player, region, dimension);
        }
    }

    private void startRtp(Player player, String region, String dimension) {
        String normalizedRegion = region.toLowerCase(Locale.ROOT);
        String normalizedDimension = dimension.toLowerCase(Locale.ROOT);
        String mapped = plugin.settings().servers().get(region.toLowerCase());
        String targetServer = mapped != null ? mapped : plugin.settings().serverName();
        plugin.auditLogger().log("rtp-select player=" + player.getName() + " region=" + normalizedRegion + " dimension=" + normalizedDimension + " targetServer=" + targetServer);
        if (!targetServer.equalsIgnoreCase(plugin.settings().serverName())) {
            NetworkPacket packet = NetworkPacket.request(PacketType.TRANSFER_PLAYER, plugin.settings().serverName(), "velocity")
                    .put("uuid", player.getUniqueId().toString())
                    .put("targetServer", targetServer)
                    .put("postAction", "RTP_" + normalizedDimension.toUpperCase(Locale.ROOT))
                    .put("dimension", normalizedDimension)
                    .put("region", normalizedRegion);
            plugin.messaging().send(packet);
            return;
        }
        plugin.services().teleportService().executeLocalRtp(player, normalizedRegion, normalizedDimension);
    }
}
