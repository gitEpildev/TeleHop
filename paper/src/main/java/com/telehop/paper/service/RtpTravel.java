package com.telehop.paper.service;

import com.telehop.common.PermissionNodes;
import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import com.telehop.paper.NetworkPaperPlugin;
import com.telehop.paper.config.RtpPolicy;
import com.telehop.paper.config.TravelVariant;
import com.telehop.paper.gui.RtpGui;
import com.telehop.paper.platform.PlatformText;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Shared authorisation, cooldown, warmup and routing for configured and ordinary RTP. */
public final class RtpTravel {
    private final NetworkPaperPlugin plugin;

    public RtpTravel(NetworkPaperPlugin plugin) { this.plugin = plugin; }

    private boolean ready(Player player, TravelVariant variant) {
        if (!plugin.isFeatureEnabled("rtp")) return refuse(player, "feature-disabled");
        if (!player.hasPermission(PermissionNodes.RTP) || variant != null
                && (!variant.equals(plugin.commandNames().variants().get(variant.id()))
                || !RtpPolicy.variantAccess(player, variant, plugin.permissionService()))) return refuse(player, "no-permission");
        if (plugin.rtpManager().hasPendingRtp(player)) return refuse(player, "rtp-in-progress");
        if (!player.hasPermission(PermissionNodes.RTP_BYPASS_COOLDOWN) && plugin.rtpManager().onCooldown(player)) {
            PlatformText.send(player, plugin.msg("rtp-cooldown", Map.of("seconds", String.valueOf(plugin.rtpManager().remainingCooldown(player)))));
            return false;
        }
        return true;
    }

    private boolean refuse(Player player, String message) {
        PlatformText.send(player, plugin.msg(message));
        return false;
    }

    public void open(Player player, TravelVariant variant) {
        if (!ready(player, variant)) return;
        if (variant == null || variant.gui()) {
            PlatformText.send(player, plugin.msg("rtp-opening"));
            new RtpGui(plugin, variant, (region, dim) -> select(player, variant, region, dim)).openRegion(player);
        } else {
            String dimension = variant.world() == null ? Objects.requireNonNullElse(variant.dimension(), plugin.commandNames().rtp().defaultDimension()) : "";
            select(player, variant, Objects.requireNonNullElse(variant.region(), ""), dimension);
        }
    }

    private void select(Player player, TravelVariant variant, String region, String dimension) {
        if (!ready(player, variant)) return;
        var choice = plugin.commandNames().rtp().resolve(variant, region, dimension);
        if (!authorised(player, variant, choice)) { refuse(player, "no-permission"); return; }
        var settings = plugin.settings();
        int delay = variant != null && variant.delay() != null ? variant.delay() : settings.rtpDelaySeconds();
        int cooldown = variant != null && variant.cooldown() != null ? variant.cooldown() : settings.rtpCooldownSeconds();
        boolean cancel = variant != null && variant.cancelOnMove() != null ? variant.cancelOnMove() : settings.rtpCancelOnMove();
        if (player.hasPermission(PermissionNodes.RTP_BYPASS_DELAY)) delay = 0;
        plugin.rtpManager().markPendingRtp(player, delay);
        plugin.rtpManager().markCooldown(player, cooldown);
        Runnable execute = () -> {
            if (!authorised(player, variant, choice)) {
                plugin.rtpManager().clearPendingRtp(player);
                refuse(player, "no-permission");
                return;
            }
            start(player, variant, choice).whenCompleteAsync((success, error) -> {
                plugin.rtpManager().clearPendingRtp(player);
                if (error != null && player.isOnline()) refuse(player, "rtp-failed");
            }, task -> Bukkit.getScheduler().runTask(plugin, task));
        };
        if (delay == 0) { execute.run(); return; }
        PlatformText.send(player, plugin.msg("rtp-delay", Map.of("seconds", String.valueOf(delay))));
        new WarmupTask(plugin, player, delay, cancel, settings.showCountdown(), execute, () -> {
            plugin.rtpManager().clearPendingRtp(player);
            refuse(player, "rtp-cancelled");
        }).start();
    }

    private boolean authorised(Player player, TravelVariant variant, RtpPolicy.Choice previous) {
        if (previous == null || !plugin.isFeatureEnabled("rtp") || !player.isOnline()) return false;
        if (variant != null && !variant.equals(plugin.commandNames().variants().get(variant.id()))) return false;
        var current = plugin.commandNames().rtp().resolve(variant, previous.region(), previous.dimension());
        return previous.equals(current) && plugin.commandNames().rtp().allowedHere(player, variant, current, plugin.permissionService());
    }

    private CompletableFuture<Boolean> start(Player player, TravelVariant variant, RtpPolicy.Choice choice) {
        if (choice.server().equalsIgnoreCase(plugin.settings().serverName())) return place(player, variant, choice);
        if (variant != null) return VariantTravel.transfer(plugin, player, variant, choice.server(),
                Map.of("region", choice.region(), "dimension", choice.dimension()));
        var packet = NetworkPacket.request(PacketType.TRANSFER_PLAYER, plugin.settings().serverName(), "velocity")
                .put("uuid", player.getUniqueId().toString()).put("targetServer", choice.server())
                .put("postAction", "RTP")
                .put("region", choice.region()).put("dimension", choice.dimension());
        boolean sent = plugin.messaging().send(player, packet);
        if (!sent) refuse(player, "rtp-failed");
        return CompletableFuture.completedFuture(sent);
    }

    /** Re-evaluate the local configuration and active permissions after the player changes servers. */
    public CompletableFuture<Boolean> arrive(Player player, TravelVariant variant, String region, String dimension) {
        var policy = plugin.commandNames().rtp();
        // 3.x proxies supplied this sentinel when the sender omitted its region.
        if (variant == null && "default".equals(region) && !policy.regions().containsKey(region)) region = null;
        var choice = policy.resolve(variant, region, dimension);
        if (!authorised(player, variant, choice) || !choice.server().equalsIgnoreCase(plugin.settings().serverName())) {
            refuse(player, "rtp-failed");
            return CompletableFuture.completedFuture(false);
        }
        int cooldown = variant != null && variant.cooldown() != null ? variant.cooldown() : plugin.settings().rtpCooldownSeconds();
        plugin.rtpManager().markCooldown(player, cooldown);
        return place(player, variant, choice);
    }

    private CompletableFuture<Boolean> place(Player player, TravelVariant variant, RtpPolicy.Choice choice) {
        return plugin.services().teleportService().executeLocalRtp(player, choice, () -> authorised(player, variant, choice));
    }
}
