package com.telehop.paper.command.admin;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import com.telehop.common.PermissionNodes;
import com.telehop.paper.NetworkPaperPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Map;

@CommandAlias("forcedelhome")
@CommandPermission(PermissionNodes.ADMIN)
public class ForceDelHomeCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public ForceDelHomeCommand(NetworkPaperPlugin plugin) {
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
                    sender.sendMessage(plugin.msg("forcedelhome-no-homes", Map.of("player", playerName)));
                    return;
                }
                sender.sendMessage(plugin.msg("forcedelhome-header", Map.of("player", playerName, "count", String.valueOf(homes.size()))));
                for (var home : homes) {
                    Component line = plugin.messageService().deserialize(
                            "<gray>  Home " + home.slot() + " <dark_gray>(" + home.server() + " " +
                                    (int) home.x() + "," + (int) home.y() + "," + (int) home.z() + ") ")
                            .append(plugin.messageService().deserialize("<red><bold>[DELETE]</bold></red>")
                                    .clickEvent(ClickEvent.runCommand("/forcedelhome-confirm " + uuid + " " + home.slot())));
                    sender.sendMessage(line);
                }
            });
        });
    }

    @co.aikar.commands.annotation.Subcommand("confirm")
    @CommandAlias("forcedelhome-confirm")
    public void confirm(Player sender, String uuid, int slot) {
        plugin.services().homeService().delete(uuid, slot).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(plugin.msg("forcedelhome-deleted", Map.of("slot", String.valueOf(slot))))));
    }
}
