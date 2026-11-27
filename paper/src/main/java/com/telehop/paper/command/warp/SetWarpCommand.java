package com.telehop.paper.command.warp;

import com.telehop.paper.platform.PlatformText;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import com.telehop.common.model.WarpRecord;
import com.telehop.common.PermissionNodes;
import com.telehop.paper.NetworkPaperPlugin;
import org.bukkit.entity.Player;

import java.util.Map;

@CommandAlias("%{cmd-setwarp}")
@CommandPermission(PermissionNodes.ADMIN_WARPS_SET)
public class SetWarpCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public SetWarpCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Default
    public void execute(Player player, String name) {
        if (!plugin.isFeatureEnabled("warps")) {
            PlatformText.send(player, plugin.msg("feature-disabled"));
            return;
        }
        var loc = player.getLocation();
        WarpRecord warp = new WarpRecord(
                name,
                plugin.settings().serverName(),
                plugin.versionAdapter().getWorldName(loc.getWorld()),
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch()
        );
        plugin.warpService().upsert(warp).thenRunAsync(() ->
                PlatformText.send(player, plugin.msg("warp-created", Map.of("name", name))), plugin::onMainThread);
    }
}
