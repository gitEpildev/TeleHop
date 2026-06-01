package com.telehop.paper.command.admin;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import com.telehop.common.PermissionNodes;
import com.telehop.common.model.HomeRecord;
import com.telehop.paper.NetworkPaperPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Map;

@CommandAlias("forcesethome")
@CommandPermission(PermissionNodes.ADMIN)
public class ForceSetHomeCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public ForceSetHomeCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Default
    @CommandCompletion("@networkplayers")
    public void execute(Player sender, String playerName, String homeName) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        String uuid = target.getUniqueId().toString();
        Location loc = sender.getLocation();

        if (loc.getWorld() == null) return;

        HomeRecord home = new HomeRecord(uuid, homeName,
                plugin.settings().serverName(), loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());

        plugin.services().homeService().upsert(home).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(plugin.msg("forcesethome-set", Map.of(
                                "player", playerName, "name", homeName)))));
    }
}
