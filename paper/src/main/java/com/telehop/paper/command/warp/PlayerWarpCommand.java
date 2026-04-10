package com.telehop.paper.command.warp;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import com.telehop.common.PermissionNodes;
import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import com.telehop.common.model.PlayerWarpRecord;
import com.telehop.paper.NetworkPaperPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@CommandAlias("pwarp|playerwarp|pwarps")
public class PlayerWarpCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public PlayerWarpCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean checkFeature(Player player) {
        if (!plugin.isFeatureEnabled("player-warps")) {
            player.sendMessage(plugin.msg("feature-disabled"));
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
            player.sendMessage(plugin.msg("no-permission"));
            return;
        }

        String uuid = player.getUniqueId().toString();
        int limit = getWarpLimit(player);

        if (limit == 0) {
            player.sendMessage(plugin.msg("pwarp-no-permission"));
            return;
        }

        plugin.playerWarpService().countByOwner(uuid).thenAccept(count -> {
            if (limit > 0 && count >= limit) {
                player.sendMessage(plugin.msg("pwarp-limit-reached",
                        Map.of("limit", String.valueOf(limit))));
                return;
            }

            Location loc = player.getLocation();
            PlayerWarpRecord warp = new PlayerWarpRecord(
                    uuid, name,
                    plugin.settings().serverName(),
                    loc.getWorld().getName(),
                    loc.getX(), loc.getY(), loc.getZ(),
                    loc.getYaw(), loc.getPitch(),
                    false
            );
            plugin.playerWarpService().upsert(warp).thenRun(() -> {
                int newCount = count + 1;
                String limitStr = limit < 0 ? "unlimited" : String.valueOf(limit);
                player.sendMessage(plugin.msg("pwarp-created",
                        Map.of("name", name,
                                "count", String.valueOf(newCount),
                                "limit", limitStr)));
            });
        });
    }

    @Subcommand("del|delete|remove")
    @Description("Delete one of your warps")
    @CommandCompletion("@playerwarps")
    public void del(Player player, String name) {
        if (!checkFeature(player)) return;
        if (!plugin.permissionService().has(player, PermissionNodes.PWARP)) {
            player.sendMessage(plugin.msg("no-permission"));
            return;
        }

        String uuid = player.getUniqueId().toString();
        plugin.playerWarpService().find(uuid, name).thenAccept(opt -> {
            if (opt.isEmpty()) {
                player.sendMessage(plugin.msg("pwarp-not-found", Map.of("name", name)));
                return;
            }
            plugin.playerWarpService().delete(uuid, name).thenRun(() ->
                    player.sendMessage(plugin.msg("pwarp-deleted", Map.of("name", name))));
        });
    }

    @Subcommand("list")
    @Description("List your warps")
    public void list(Player player) {
        if (!checkFeature(player)) return;
        if (!plugin.permissionService().has(player, PermissionNodes.PWARP)) {
            player.sendMessage(plugin.msg("no-permission"));
            return;
        }

        String uuid = player.getUniqueId().toString();
        int limit = getWarpLimit(player);
        String limitStr = limit < 0 ? "unlimited" : String.valueOf(limit);

        plugin.playerWarpService().listByOwner(uuid).thenAccept(warps -> {
            if (warps.isEmpty()) {
                player.sendMessage(plugin.msg("pwarp-list-empty"));
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < warps.size(); i++) {
                PlayerWarpRecord w = warps.get(i);
                if (i > 0) sb.append(", ");
                sb.append(w.name());
                if (w.isPublic()) sb.append(" &7[public]");
            }
            player.sendMessage(plugin.msg("pwarp-list-header",
                    Map.of("warps", sb.toString(),
                            "count", String.valueOf(warps.size()),
                            "limit", limitStr)));
        });
    }

    @Subcommand("public|toggle")
    @Description("Toggle a warp between public and private")
    @CommandCompletion("@playerwarps")
    public void togglePublic(Player player, String name) {
        if (!checkFeature(player)) return;
        if (!plugin.permissionService().has(player, PermissionNodes.PWARP)) {
            player.sendMessage(plugin.msg("no-permission"));
            return;
        }

        String uuid = player.getUniqueId().toString();
        plugin.playerWarpService().find(uuid, name).thenAccept(opt -> {
            if (opt.isEmpty()) {
                player.sendMessage(plugin.msg("pwarp-not-found", Map.of("name", name)));
                return;
            }
            boolean newState = !opt.get().isPublic();
            plugin.playerWarpService().setPublic(uuid, name, newState).thenRun(() ->
                    player.sendMessage(plugin.msg(newState ? "pwarp-made-public" : "pwarp-made-private",
                            Map.of("name", name))));
        });
    }

    @Subcommand("admin del|admin delete|admin remove")
    @CommandPermission("telehop.admin")
    @Description("Admin: delete any player's warp")
    @CommandCompletion("@networkplayers @nothing")
    public void adminDel(Player player, String targetName, String warpName) {
        @SuppressWarnings("deprecation")
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        String targetUuid = target.getUniqueId().toString();
        plugin.playerWarpService().find(targetUuid, warpName).thenAccept(opt -> {
            if (opt.isEmpty()) {
                player.sendMessage(plugin.msg("pwarp-not-found", Map.of("name", warpName)));
                return;
            }
            plugin.playerWarpService().delete(targetUuid, warpName).thenRun(() ->
                    player.sendMessage(plugin.mm("<gold>Deleted warp <aqua>" + warpName +
                            "</aqua> from player <aqua>" + targetName + "</aqua>.")));
        });
    }

    @Default
    @CatchUnknown
    @CommandCompletion("@playerwarps")
    public void teleport(Player player, String[] args) {
        if (!checkFeature(player)) return;
        if (!plugin.permissionService().has(player, PermissionNodes.PWARP)) {
            player.sendMessage(plugin.msg("no-permission"));
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
        plugin.playerWarpService().find(uuid, name).thenAccept(opt -> {
            if (opt.isEmpty()) {
                player.sendMessage(plugin.msg("pwarp-not-found", Map.of("name", name)));
                return;
            }
            doTeleport(player, opt.get());
        });
    }

    private void teleportToOther(Player player, String targetName, String warpName) {
        @SuppressWarnings("deprecation")
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target.getUniqueId() == null) {
            player.sendMessage(plugin.msg("pwarp-player-not-found", Map.of("player", targetName)));
            return;
        }

        String targetUuid = target.getUniqueId().toString();
        plugin.playerWarpService().findPublic(targetUuid, warpName).thenAccept(opt -> {
            if (opt.isEmpty()) {
                player.sendMessage(plugin.msg("pwarp-not-yours", Map.of("name", warpName)));
                return;
            }
            doTeleport(player, opt.get());
        });
    }

    private void doTeleport(Player player, PlayerWarpRecord warp) {
        if (warp.server().equalsIgnoreCase(plugin.settings().serverName())) {
            World world = Bukkit.getWorld(warp.world());
            if (world == null) {
                player.sendMessage(plugin.msg("pwarp-not-found", Map.of("name", warp.name())));
                return;
            }
            player.sendMessage(plugin.msg("pwarp-teleporting", Map.of("name", warp.name())));
            Location loc = new Location(world, warp.x(), warp.y(), warp.z(), warp.yaw(), warp.pitch());
            Bukkit.getScheduler().runTask(plugin, () -> player.teleportAsync(loc));
        } else {
            player.sendMessage(plugin.msg("pwarp-teleporting", Map.of("name", warp.name())));
            NetworkPacket packet = NetworkPacket.request(
                    PacketType.TRANSFER_PLAYER, plugin.settings().serverName(), "velocity")
                    .put("uuid", player.getUniqueId().toString())
                    .put("targetServer", warp.server())
                    .put("postAction", "PWARP")
                    .put("pwarpOwner", warp.ownerUuid())
                    .put("pwarpName", warp.name());
            plugin.messaging().send(packet);
        }
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
