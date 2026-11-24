package com.telehop.paper.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import com.telehop.common.PermissionNodes;
import com.telehop.paper.NetworkPaperPlugin;
import org.bukkit.entity.Player;

/** Entry point for default and named spawn travel. */
@CommandAlias("%{cmd-spawn}")
@CommandPermission(PermissionNodes.SPAWN)
public class SpawnCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;
    public SpawnCommand(NetworkPaperPlugin plugin) { this.plugin = plugin; }

    @Default
    @CommandCompletion("@spawns")
    public void execute(Player player, @Optional String id) {
        plugin.services().spawns().travel(player, id, false);
    }
}
