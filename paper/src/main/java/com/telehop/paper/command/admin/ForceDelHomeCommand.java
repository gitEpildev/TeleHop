package com.telehop.paper.command.admin;

import com.telehop.paper.platform.PlatformText;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import com.telehop.common.PermissionNodes;
import com.telehop.paper.NetworkPaperPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Map;

@CommandAlias("%{cmd-forcedelhome}")
@CommandPermission(PermissionNodes.ADMIN_HOMES_DELETE)
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
                    PlatformText.send(sender, plugin.msg("forcedelhome-no-homes", Map.of("player", playerName)));
                    return;
                }
                PlatformText.send(sender, plugin.msg("forcedelhome-header", Map.of("player", playerName, "count", String.valueOf(homes.size()))));
                for (var home : homes) {
                    String details = "(" + home.server() + " " + (int) home.x() + ", "
                            + (int) home.y() + ", " + (int) home.z() + ") ";
                    Component line = Component.text("  " + home.name() + " ", NamedTextColor.GRAY)
                            .append(Component.text(details, NamedTextColor.DARK_GRAY))
                            .append(plugin.messageService().raw("homes-delete-button")
                                    .clickEvent(ClickEvent.runCommand("/" + plugin.commandNames().primary("forcedelhome-confirm") + " " + uuid + " " + HomeCommandArgument.encode(home.name()))));
                    PlatformText.send(sender, line);
                }
            });
        });
    }

    @co.aikar.commands.annotation.Subcommand("confirm")
    @CommandAlias("%{cmd-forcedelhome-confirm}")
    public void confirm(Player sender, String uuid, String argument, @co.aikar.commands.annotation.Optional String action) {
        if (!sender.hasPermission(PermissionNodes.ADMIN_HOMES_DELETE)) {
            PlatformText.send(sender, plugin.msg("no-permission"));
            return;
        }
        String name = HomeCommandArgument.decode(argument);
        if (!"confirm".equals(action)) {
            PlatformText.send(sender, plugin.msg("homes-confirm", Map.of("name", name)).clickEvent(ClickEvent.runCommand("/"
                    + plugin.commandNames().primary("forcedelhome-confirm") + " " + uuid + " " + HomeCommandArgument.encode(name) + " confirm")));
            return;
        }
        plugin.services().homeService().delete(uuid, name).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () ->
                        PlatformText.send(sender, plugin.msg("forcedelhome-deleted", Map.of("name", name)))));
    }
}
