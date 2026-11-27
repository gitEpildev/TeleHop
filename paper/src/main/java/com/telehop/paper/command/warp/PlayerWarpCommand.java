package com.telehop.paper.command.warp;

import com.telehop.paper.platform.PlatformText;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import com.telehop.common.PermissionNodes;
import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import com.telehop.common.model.PlayerWarpRecord;
import com.telehop.paper.NetworkPaperPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Map;

@CommandAlias("%{cmd-pwarp}")
public class PlayerWarpCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public PlayerWarpCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean checkFeature(Player player) {
        if (!plugin.isFeatureEnabled("player-warps")) {
            PlatformText.send(player, plugin.msg("feature-disabled"));
            return false;
        }
        return true;
    }

    @Subcommand("set|create")
    @Description("Create a personal warp at your location")
    @CommandCompletion("")
    public void set(Player player, String name) {
        if (!checkFeature(player)) return;
        if (!plugin.permissionService().has(player, PermissionNodes.PWARP)) {
            PlatformText.send(player, plugin.msg("no-permission"));
            return;
        }

        String uuid = player.getUniqueId().toString();
        int limit = getWarpLimit(player);

        if (limit == 0) {
            PlatformText.send(player, plugin.msg("pwarp-no-permission"));
            return;
        }

        Location loc = player.getLocation();
        plugin.playerWarpService().countByOwner(uuid).thenAcceptAsync(count -> {
            if (!player.isOnline() || !player.hasPermission(PermissionNodes.PWARP)) return;
            if (limit > 0 && count >= limit) {
                PlatformText.send(player, plugin.msg("pwarp-limit-reached",
                        Map.of("limit", String.valueOf(limit))));
                return;
            }

            PlayerWarpRecord warp = new PlayerWarpRecord(
                    uuid, name,
                    plugin.settings().serverName(),
                    plugin.versionAdapter().getWorldName(loc.getWorld()),
                    loc.getX(), loc.getY(), loc.getZ(),
                    loc.getYaw(), loc.getPitch(),
                    false
            );
            plugin.playerWarpService().upsert(warp).thenRunAsync(() -> {
                int newCount = count + 1;
                PlatformText.send(player, plugin.messageService().format("pwarp-created", Map.of("name", name,
                        "count", String.valueOf(newCount)), Map.of("limit", limitText(limit))));
            }, plugin::onMainThread);
        }, plugin::onMainThread);
    }

    @Subcommand("del|delete|remove")
    @Description("Delete one of your warps")
    @CommandCompletion("@playerwarps")
    public void del(Player player, String name) {
        if (!checkFeature(player)) return;
        if (!plugin.permissionService().has(player, PermissionNodes.PWARP)) {
            PlatformText.send(player, plugin.msg("no-permission"));
            return;
        }

        String uuid = player.getUniqueId().toString();
        plugin.playerWarpService().find(uuid, name).thenAcceptAsync(opt -> {
            if (opt.isEmpty()) {
                PlatformText.send(player, plugin.msg("pwarp-not-found", Map.of("name", name)));
                return;
            }
            plugin.playerWarpService().delete(uuid, name).thenRunAsync(() ->
                    PlatformText.send(player, plugin.msg("pwarp-deleted", Map.of("name", name))), plugin::onMainThread);
        }, plugin::onMainThread);
    }

    @Subcommand("list")
    @Description("List your warps")
    public void list(Player player) {
        if (!checkFeature(player)) return;
        if (!plugin.permissionService().has(player, PermissionNodes.PWARP)) {
            PlatformText.send(player, plugin.msg("no-permission"));
            return;
        }

        String uuid = player.getUniqueId().toString();
        int limit = getWarpLimit(player);

        plugin.playerWarpService().listByOwner(uuid).thenAcceptAsync(warps -> {
            if (warps.isEmpty()) {
                PlatformText.send(player, plugin.msg("pwarp-list-empty"));
                return;
            }
            var messages = plugin.messageService();
            var items = warps.stream().map(warp -> {
                var name = Component.text(warp.name());
                return warp.isPublic() ? name.appendSpace().append(messages.raw("warp-public-label")) : name;
            }).toList();
            var list = Component.join(JoinConfiguration.separator(Component.text(", ")), items);
            PlatformText.send(player, messages.format("pwarp-list-header", Map.of("count", String.valueOf(warps.size())),
                    Map.of("warps", list, "limit", limitText(limit))));
        }, plugin::onMainThread);
    }

    @Subcommand("public|toggle")
    @Description("Toggle a warp between public and private")
    @CommandCompletion("@playerwarps")
    public void togglePublic(Player player, String name) {
        if (!checkFeature(player)) return;
        if (!plugin.permissionService().has(player, PermissionNodes.PWARP)) {
            PlatformText.send(player, plugin.msg("no-permission"));
            return;
        }

        String uuid = player.getUniqueId().toString();
        plugin.playerWarpService().find(uuid, name).thenAcceptAsync(opt -> {
            if (opt.isEmpty()) {
                PlatformText.send(player, plugin.msg("pwarp-not-found", Map.of("name", name)));
                return;
            }
            boolean newState = !opt.get().isPublic();
            plugin.playerWarpService().setPublic(uuid, name, newState).thenRunAsync(() ->
                    PlatformText.send(player, plugin.msg(newState ? "pwarp-made-public" : "pwarp-made-private",
                            Map.of("name", name))), plugin::onMainThread);
        }, plugin::onMainThread);
    }

    @Subcommand("admin del|admin delete|admin remove")
    @CommandPermission(PermissionNodes.ADMIN_PLAYERWARPS_DELETE)
    @Description("Admin: delete any player's warp")
    @CommandCompletion("@networkplayers @nothing")
    public void adminDel(Player player, String targetName, String warpName) {
        @SuppressWarnings("deprecation")
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        String targetUuid = target.getUniqueId().toString();
        plugin.playerWarpService().find(targetUuid, warpName).thenAcceptAsync(opt -> {
            if (opt.isEmpty()) {
                PlatformText.send(player, plugin.msg("pwarp-not-found", Map.of("name", warpName)));
                return;
            }
            plugin.playerWarpService().delete(targetUuid, warpName).thenRunAsync(() ->
                    PlatformText.send(player, plugin.msg("forcedelwarp-deleted", Map.of("name", warpName, "player", targetName))), plugin::onMainThread);
        }, plugin::onMainThread);
    }

    @Default
    @CatchUnknown
    @CommandCompletion("@playerwarps")
    public void teleport(Player player, String[] args) {
        if (!checkFeature(player)) return;
        if (!plugin.permissionService().has(player, PermissionNodes.PWARP)) {
            PlatformText.send(player, plugin.msg("no-permission"));
            return;
        }

        if (args.length == 0) {
            list(player);
            return;
        }

        if (args.length == 1) {
            teleportToOwn(player, args[0]);
        } else {
            teleportToOther(player, args[0], args[1]);
        }
    }

    private void teleportToOwn(Player player, String name) {
        String uuid = player.getUniqueId().toString();
        plugin.playerWarpService().find(uuid, name).thenAccept(opt -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (opt.isEmpty()) {
                PlatformText.send(player, plugin.msg("pwarp-not-found", Map.of("name", name)));
                return;
            }
            doTeleport(player, opt.get());
        }));
    }

    private void teleportToOther(Player player, String targetName, String warpName) {
        @SuppressWarnings("deprecation")
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target.getUniqueId() == null) {
            PlatformText.send(player, plugin.msg("pwarp-player-not-found", Map.of("player", targetName)));
            return;
        }

        String targetUuid = target.getUniqueId().toString();
        plugin.playerWarpService().findPublic(targetUuid, warpName).thenAccept(opt -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (opt.isEmpty()) {
                PlatformText.send(player, plugin.msg("pwarp-not-yours", Map.of("name", warpName)));
                return;
            }
            doTeleport(player, opt.get());
        }));
    }

    private void doTeleport(Player player, PlayerWarpRecord warp) {
        if (warp.server().equalsIgnoreCase(plugin.settings().serverName())) {
            World world = plugin.versionAdapter().resolveWorld(warp.world());
            if (world == null) {
                PlatformText.send(player, plugin.msg("pwarp-not-found", Map.of("name", warp.name())));
                return;
            }
            PlatformText.send(player, plugin.msg("pwarp-teleporting", Map.of("name", warp.name())));
            Location loc = new Location(world, warp.x(), warp.y(), warp.z(), warp.yaw(), warp.pitch());
            plugin.platform().teleport(player, loc);
        } else {
            PlatformText.send(player, plugin.msg("pwarp-teleporting", Map.of("name", warp.name())));
            NetworkPacket packet = NetworkPacket.request(
                    PacketType.TRANSFER_PLAYER, plugin.settings().serverName(), "velocity")
                    .put("uuid", player.getUniqueId().toString())
                    .put("targetServer", warp.server())
                    .put("postAction", "PWARP")
                    .put("pwarpOwner", warp.ownerUuid())
                    .put("pwarpName", warp.name());
            plugin.messaging().send(player, packet);
        }
    }

    private Component limitText(int limit) {
        return limit < 0 ? plugin.messageService().raw("pwarp-unlimited") : Component.text(limit);
    }

    private int getWarpLimit(Player player) {
        if (player.hasPermission("telehop.warps.unlimited")) return -1;

        int highest = 0;
        for (var perm : player.getEffectivePermissions()) {
            String node = perm.getPermission();
            if (!node.startsWith("telehop.warps.") || !perm.getValue()) continue;
            String suffix = node.substring("telehop.warps.".length());
            if (suffix.equals("unlimited")) return -1;
            try {
                int val = Integer.parseInt(suffix);
                if (val > highest) highest = val;
            } catch (NumberFormatException ignored) {
            }
        }
        return highest;
    }
}
