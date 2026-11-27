package com.telehop.paper.command.warp;

import com.telehop.paper.platform.PlatformText;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import com.telehop.common.PermissionNodes;
import com.telehop.paper.NetworkPaperPlugin;
import org.bukkit.entity.Player;

import java.util.Map;

@CommandAlias("%{cmd-delwarp}")
@CommandPermission(PermissionNodes.ADMIN_WARPS_DELETE)
public class DelWarpCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public DelWarpCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Default
    @CommandCompletion("@warps")
    public void execute(Player player, String name) {
        if (!plugin.isFeatureEnabled("warps")) {
            PlatformText.send(player, plugin.msg("feature-disabled"));
            return;
        }
        plugin.warpService().find(name).thenAcceptAsync(opt -> {
            if (opt.isEmpty()) {
                PlatformText.send(player, plugin.msg("warp-not-found", Map.of("name", name)));
                return;
            }
            plugin.warpService().delete(name).thenRunAsync(() ->
                    PlatformText.send(player, plugin.msg("warp-deleted", Map.of("name", name))), plugin::onMainThread);
        }, plugin::onMainThread);
    }
}
