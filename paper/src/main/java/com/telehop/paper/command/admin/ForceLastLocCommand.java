package com.telehop.paper.command.admin;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import com.telehop.common.PermissionNodes;
import com.telehop.common.model.LastLocationRecord;
import com.telehop.paper.NetworkPaperPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Map;

@CommandAlias("forcelastloc|forcell")
@CommandPermission(PermissionNodes.ADMIN)
public class ForceLastLocCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public ForceLastLocCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Default
    @CommandCompletion("@networkplayers")
    public void info(Player sender, String playerName) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        String uuid = target.getUniqueId().toString();

        plugin.services().lastLocationService().find(uuid).thenAccept(opt -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (opt.isEmpty()) {
                    sender.sendMessage(plugin.msg("forcelastloc-no-data", Map.of("player", playerName)));
                    return;
                }
                LastLocationRecord loc = opt.get();
                sender.sendMessage(plugin.msg("forcelastloc-info", Map.of(
                        "player", playerName,
                        "server", loc.server(),
                        "world", loc.world(),
                        "x", String.valueOf((int) loc.x()),
                        "y", String.valueOf((int) loc.y()),
                        "z", String.valueOf((int) loc.z())
                )));
            });
        });
    }

    @Subcommand("tp")
    @CommandCompletion("@networkplayers")
    public void teleport(Player sender, String playerName) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        String uuid = target.getUniqueId().toString();

        plugin.services().lastLocationService().find(uuid).thenAccept(opt -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (opt.isEmpty()) {
                    sender.sendMessage(plugin.msg("forcelastloc-no-data", Map.of("player", playerName)));
                    return;
                }
                LastLocationRecord loc = opt.get();
                String currentServer = plugin.settings().serverName();

                if (!loc.server().equalsIgnoreCase(currentServer)) {
                    var packet = com.telehop.common.model.NetworkPacket.request(
                            com.telehop.common.model.PacketType.TRANSFER_PLAYER,
                            currentServer, "velocity");
                    packet.put("uuid", sender.getUniqueId().toString());
                    packet.put("targetServer", loc.server());
                    packet.put("postAction", "LASTLOC");
                    packet.put("world", loc.world());
                    packet.put("x", String.valueOf(loc.x()));
                    packet.put("y", String.valueOf(loc.y()));
                    packet.put("z", String.valueOf(loc.z()));
                    packet.put("yaw", String.valueOf(loc.yaw()));
                    packet.put("pitch", String.valueOf(loc.pitch()));
                    plugin.messaging().send(packet);
                    sender.sendMessage(plugin.msg("forcelastloc-tp", Map.of("player", playerName, "server", loc.server())));
                    return;
                }

                org.bukkit.World world = plugin.versionAdapter().resolveWorld(loc.world());
                if (world == null) {
                    sender.sendMessage(plugin.msg("lastloc-world-missing"));
                    return;
                }
                org.bukkit.Location dest = new org.bukkit.Location(world, loc.x(), loc.y(), loc.z(), loc.yaw(), loc.pitch());
                sender.teleportAsync(dest);
                sender.sendMessage(plugin.msg("forcelastloc-tp", Map.of("player", playerName, "server", currentServer)));
            });
        });
    }

    @Subcommand("clear")
    @CommandCompletion("@networkplayers")
    public void clear(Player sender, String playerName) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        String uuid = target.getUniqueId().toString();

        plugin.services().lastLocationService().delete(uuid).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(plugin.msg("forcelastloc-cleared", Map.of("player", playerName)))));
    }
}
