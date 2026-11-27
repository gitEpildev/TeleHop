package com.telehop.paper.command.admin;

import com.telehop.paper.platform.PlatformText;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import com.telehop.common.PermissionNodes;
import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import com.telehop.paper.NetworkPaperPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

@CommandAlias("%{cmd-tp}")
public class AdminTeleportCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public AdminTeleportCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Unified handler for all /tp variants:
     *   /tp <player>                  - sender → player
     *   /tp <player1> <player2>       - player1 → player2
     *   /tp <x> <y> <z>              - sender → coordinates
     *   /tp <player> <x> <y> <z>    - player → coordinates
     */
    @Default
    @co.aikar.commands.annotation.CommandCompletion("@networkplayers @networkplayers")
    public void execute(Player sender, String arg1,
                        @co.aikar.commands.annotation.Optional String arg2,
                        @co.aikar.commands.annotation.Optional String arg3,
                        @co.aikar.commands.annotation.Optional String arg4) {
        if (!plugin.isFeatureEnabled("admin-tp")) {
            PlatformText.send(sender, plugin.msg("feature-disabled"));
            return;
        }
        boolean coordinates = arg3 != null;
        boolean self = coordinates ? arg4 == null || sender.getName().equalsIgnoreCase(arg1) : arg2 == null;
        String permission;
        if (coordinates) {
            permission = self ? PermissionNodes.TP_COORDINATES_SELF : PermissionNodes.TP_COORDINATES_OTHERS;
        } else {
            permission = self ? PermissionNodes.TP_PLAYER_SELF : PermissionNodes.TP_PLAYER_OTHERS;
        }
        if (!sender.hasPermission(permission)) {
            PlatformText.send(sender, plugin.msg("no-permission"));
            return;
        }

        // /tp <player> <x> <y> <z>
        if (arg4 != null) {
            Double x = parseDouble(arg2), y = parseDouble(arg3), z = parseDouble(arg4);
            if (x == null || y == null || z == null) {
                PlatformText.send(sender, plugin.msg("invalid-coords"));
                return;
            }
            tpPlayerToCoords(sender, arg1, x, y, z);
            return;
        }

        // /tp <x> <y> <z>  (three args, first is a number)
        if (arg3 != null) {
            Double x = parseDouble(arg1), y = parseDouble(arg2), z = parseDouble(arg3);
            if (x != null && y != null && z != null) {
                Location dest = new Location(sender.getWorld(), x, y, z);
                plugin.platform().teleport(sender, dest);
                plugin.auditLogger().log(sender.getName() + " tp -> " + x + " " + y + " " + z);
                return;
            }
            // Not valid coords - fall through to show usage
            PlatformText.send(sender, plugin.msg("invalid-coords"));
            return;
        }

        // /tp <player1> <player2>
        if (arg2 != null) {
            Player player1 = Bukkit.getPlayerExact(arg1);
            Player player2 = Bukkit.getPlayerExact(arg2);
            if (player1 != null && player2 != null) {
                plugin.platform().teleport(player1, player2.getLocation());
                plugin.auditLogger().log(sender.getName() + " tp " + player1.getName() + " -> " + player2.getName());
                return;
            }
            NetworkPacket packet = NetworkPacket.request(PacketType.ADMIN_TP_REQUEST, plugin.settings().serverName(), "velocity")
                    .put("mode", "PLAYER_TO_PLAYER")
                    .put("actorUuid", sender.getUniqueId().toString())
                    .put("playerName", arg1)
                    .put("targetName", arg2);
            plugin.messaging().send(packet);
            return;
        }

        // /tp <player>
        Player target = Bukkit.getPlayerExact(arg1);
        if (target != null) {
            plugin.platform().teleport(sender, target.getLocation());
            plugin.auditLogger().log(sender.getName() + " tp -> " + target.getName());
            return;
        }
        NetworkPacket packet = NetworkPacket.request(PacketType.ADMIN_TP_REQUEST, plugin.settings().serverName(), "velocity")
                .put("mode", "SELF_TO_TARGET")
                .put("senderUuid", sender.getUniqueId().toString())
                .put("targetName", arg1);
        plugin.messaging().send(packet);
    }

    /** Teleport a named player (possibly on another server) to coordinates in the sender's world. */
    private void tpPlayerToCoords(Player sender, String playerName, double x, double y, double z) {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target != null) {
            Location dest = new Location(sender.getWorld(), x, y, z);
            plugin.platform().teleport(target, dest);
            plugin.auditLogger().log(sender.getName() + " tp " + target.getName() + " -> " + x + " " + y + " " + z);
            return;
        }
        NetworkPacket packet = NetworkPacket.request(PacketType.ADMIN_TP_TO_COORDS, plugin.settings().serverName(), "velocity")
                .put("actorUuid", sender.getUniqueId().toString())
                .put("targetName", playerName)
                .put("world", plugin.versionAdapter().getWorldName(sender.getWorld()))
                .put("x", String.valueOf(x))
                .put("y", String.valueOf(y))
                .put("z", String.valueOf(z));
        plugin.messaging().send(packet);
    }

    private static Double parseDouble(String s) {
        if (s == null) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
