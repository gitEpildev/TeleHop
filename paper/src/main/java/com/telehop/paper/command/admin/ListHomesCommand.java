package com.telehop.paper.command.admin;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import com.telehop.common.PermissionNodes;
import com.telehop.paper.NetworkPaperPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Map;

@CommandAlias("%{cmd-listhomes}")
public class ListHomesCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public ListHomesCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @CommandPermission(PermissionNodes.ADMIN_HOMES_LIST)
    @Default
    @CommandCompletion("@networkplayers")
    public void execute(Player sender, String playerName) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        String uuid = target.getUniqueId().toString();

        plugin.services().homeService().listByPlayer(uuid).thenAccept(homes -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (homes.isEmpty()) {
                    sender.sendMessage(plugin.msg("listhomes-empty", Map.of("player", playerName)));
                    return;
                }
                sender.sendMessage(plugin.msg("listhomes-header", Map.of(
                        "player", playerName, "count", String.valueOf(homes.size()))));
                for (var home : homes) {
                    Component line = plugin.messageService().deserialize(
                            "<gray>  " + home.name() + " <dark_gray>(" + home.server() + " " +
                                    home.world() + " " +
                                    (int) home.x() + ", " + (int) home.y() + ", " + (int) home.z() + ") ");
                    if (sender.hasPermission(PermissionNodes.ADMIN_HOMES_VISIT)) {
                        line = line.append(plugin.messageService().raw("homes-visit-button").clickEvent(ClickEvent.runCommand("/"
                                + plugin.commandNames().primary("listhomes") + " visit " + uuid + " " + home.name())));
                    }
                    if (sender.hasPermission(PermissionNodes.ADMIN_HOMES_DELETE)) {
                        line = line.append(plugin.messageService().raw("homes-delete-button").clickEvent(ClickEvent.runCommand("/"
                                + plugin.commandNames().primary("forcedelhome-confirm") + " " + uuid + " " + home.name())));
                    }
                    sender.sendMessage(line);
                }
            });
        });
    }

    @Subcommand("visit")
    @CommandPermission(PermissionNodes.ADMIN_HOMES_VISIT)
    public void visit(Player sender, String owner, String name) {
        plugin.services().homeService().find(owner, name).thenAccept(found -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!sender.isOnline() || !sender.hasPermission(PermissionNodes.ADMIN_HOMES_VISIT)) return;
            var home = found.orElse(null);
            if (home == null) {
                sender.sendMessage(plugin.msg("home-not-found", Map.of("name", name)));
                return;
            }
            String local = plugin.settings().serverName();
            if (!home.server().equalsIgnoreCase(local)) {
                plugin.messaging().send(sender, com.telehop.common.model.NetworkPacket.request(
                        com.telehop.common.model.PacketType.TRANSFER_PLAYER, local, "velocity")
                        .put("uuid", sender.getUniqueId().toString()).put("targetServer", home.server())
                        .put("postAction", "HOME").put("homeName", name).put("homeUuid", owner)
                        .put("staffVisit", "true"));
                return;
            }
            var world = plugin.versionAdapter().resolveWorld(home.world());
            if (world == null) sender.sendMessage(plugin.msg("lastloc-world-missing"));
            else plugin.services().teleportService().teleportToHome(sender, new org.bukkit.Location(world,
                    home.x(), home.y(), home.z(), home.yaw(), home.pitch()));
        }));
    }
}
