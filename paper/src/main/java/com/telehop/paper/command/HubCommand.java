package com.telehop.paper.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import com.telehop.common.PermissionNodes;
import com.telehop.paper.NetworkPaperPlugin;
import org.bukkit.entity.Player;

/** Entry point for direct travel to the network hub. */
@CommandAlias("%{cmd-hub}")
@CommandPermission(PermissionNodes.HUB)
public class HubCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;
    public HubCommand(NetworkPaperPlugin plugin) { this.plugin = plugin; }

    @Default
    public void execute(Player player) {
        plugin.services().spawns().travel(player, null, true);
    }
}
