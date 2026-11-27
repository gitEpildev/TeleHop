package com.telehop.paper.service;

import com.telehop.common.PermissionNodes;
import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import com.telehop.paper.NetworkPaperPlugin;
import com.telehop.paper.config.RtpPolicy;
import com.telehop.paper.config.TravelVariant;
import com.telehop.paper.platform.PlatformText;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Only local definitions grant authority. The proxy carries an ID and the selected destination. */
public final class VariantTravel {
    private VariantTravel() {}

    public static void execute(NetworkPaperPlugin plugin, Player player, String id) {
        var variant = plugin.commandNames().variants().get(id);
        if (!permitted(plugin, player, variant)) {
            PlatformText.send(player, plugin.msg("no-permission"));
            return;
        }
        if (variant.kind().equals("rtp")) new RtpTravel(plugin).open(player, variant);
        else plugin.services().spawns().travel(player, variant.spawn(), variant.kind().equals("hub"), variant);
    }

    public static boolean permitted(NetworkPaperPlugin plugin, Player player, TravelVariant variant) {
        if (variant == null || !player.isOnline() || !variant.equals(plugin.commandNames().variants().get(variant.id()))
                || !RtpPolicy.variantAccess(player, variant, plugin.permissionService())) return false;
        return switch (variant.kind()) {
            case "rtp" -> plugin.isFeatureEnabled("rtp") && player.hasPermission(PermissionNodes.RTP);
            case "hub" -> plugin.isFeatureEnabled("spawn") && plugin.services().spawns().hubCommand() && player.hasPermission(PermissionNodes.HUB);
            case "spawn" -> plugin.isFeatureEnabled("spawn") && player.hasPermission(PermissionNodes.SPAWN)
                    && player.hasPermission(PermissionNodes.SPAWN_NAMED)
                    && plugin.permissionService().destination(player, PermissionNodes.SPAWN_DESTINATION + variant.spawn(),
                    variant.required().contains("destination"));
            default -> false;
        };
    }

    /** Suggestions use the same local destination restrictions as commands, without a database read. */
    public static boolean visible(NetworkPaperPlugin plugin, Player player, TravelVariant variant) {
        if (!permitted(plugin, player, variant)) return false;
        if (variant.kind().equals("rtp")) {
            return !plugin.commandNames().rtp().eligibleDimensions(player, variant, variant.region(),
                    plugin.permissionService()).isEmpty();
        }
        return !variant.kind().equals("spawn") || plugin.services().spawns().visible(player, variant);
    }

    public static CompletableFuture<Boolean> transfer(NetworkPaperPlugin plugin, Player player, TravelVariant variant,
                                                       String target, Map<String, String> context) {
        String origin = plugin.settings().serverName();
        var probe = NetworkPacket.variantProbe(origin, "velocity", player.getUniqueId());
        var world = player.getWorld();
        return plugin.messaging().sendRequest(player, probe).thenComposeAsync(reply -> {
            if (!reply.isSuccess() || !reply.supportsVariants() || !permitted(plugin, player, variant) || player.getWorld() != world)
                return CompletableFuture.completedFuture(false);
            if (variant.kind().equals("rtp")) {
                var policy = plugin.commandNames().rtp();
                var choice = policy.resolve(variant, context.get("region"), context.get("dimension"));
                if (!policy.allowedHere(player, variant, choice, plugin.permissionService()) || !target.equalsIgnoreCase(choice.server()))
                    return CompletableFuture.completedFuture(false);
            }
            var packet = NetworkPacket.request(PacketType.VARIANT_TRANSFER, origin, "velocity")
                    .put("uuid", player.getUniqueId().toString()).put("targetServer", target)
                    .put("variant", variant.id()).put("kind", variant.kind())
                    .put("errorText", PlainTextComponentSerializer.plainText().serialize(plugin.msg("spawn-unavailable")));
            context.forEach(packet::put);
            return plugin.messaging().sendRequest(player, packet).thenApply(NetworkPacket::isSuccess);
        }, task -> Bukkit.getScheduler().runTask(plugin, task)).whenCompleteAsync((ok, error) -> {
            if (player.isOnline() && (error != null || !Boolean.TRUE.equals(ok))) PlatformText.send(player, plugin.msg("spawn-unavailable"));
        }, task -> Bukkit.getScheduler().runTask(plugin, task));
    }

    public static CompletableFuture<Boolean> arrive(NetworkPaperPlugin plugin, Player player, NetworkPacket packet) {
        var id = packet.get("variant");
        var variant = id == null ? null : plugin.commandNames().variants().get(id);
        if (!permitted(plugin, player, variant) || !variant.kind().equals(packet.get("kind"))
                || !plugin.settings().serverName().equalsIgnoreCase(packet.getTargetServer())
                || !player.getUniqueId().toString().equals(packet.get("uuid"))) return CompletableFuture.completedFuture(false);
        if (variant.kind().equals("rtp")) {
            return new RtpTravel(plugin).arrive(player, variant, packet.get("region"), packet.get("dimension"));
        }
        return plugin.services().spawns().arriveVariant(player, variant, packet);
    }
}
