package com.telehop.paper.command.warp;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import com.telehop.paper.NetworkPaperPlugin;
import org.bukkit.entity.Player;

import java.util.Map;

@CommandAlias("delwarp")
@CommandPermission("telehop.admin")
public class DelWarpCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public DelWarpCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Default
    @CommandCompletion("@warps")
    public void execute(Player player, String name) {
        if (!plugin.isFeatureEnabled("warps")) {
            player.sendMessage(plugin.msg("feature-disabled"));
            return;
        }
        plugin.warpService().find(name).thenAccept(opt -> {
            if (opt.isEmpty()) {
                player.sendMessage(plugin.msg("warp-not-found", Map.of("name", name)));
                return;
            }
            plugin.warpService().delete(name).thenRun(() ->
                    player.sendMessage(plugin.msg("warp-deleted", Map.of("name", name))));
        });
    }
}
