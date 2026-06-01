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

@CommandAlias("listhomes")
@CommandPermission(PermissionNodes.ADMIN)
public class ListHomesCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public ListHomesCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

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
                    Component tpBtn = plugin.messageService().deserialize("<green><bold>[TP]</bold></green> ")
                            .clickEvent(ClickEvent.runCommand("/home " + home.name()));
                    Component delBtn = plugin.messageService().deserialize("<red><bold>[DELETE]</bold></red>")
                            .clickEvent(ClickEvent.runCommand("/forcedelhome-confirm " + uuid + " " + home.name()));
                    sender.sendMessage(line.append(tpBtn).append(delBtn));
                }
            });
        });
    }
}
