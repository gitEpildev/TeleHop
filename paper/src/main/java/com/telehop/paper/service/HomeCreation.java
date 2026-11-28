package com.telehop.paper.service;

import com.telehop.common.PermissionNodes;
import com.telehop.common.model.HomeRecord;
import com.telehop.paper.NetworkPaperPlugin;
import com.telehop.paper.platform.PlatformText;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;

/** Captures a main-thread position and applies the same atomic allowance check to commands and menu clicks. */
public final class HomeCreation {
    private HomeCreation() {}

    public static void save(NetworkPaperPlugin plugin, Player player, String name, boolean replace, Runnable reopen) {
        if (!plugin.isFeatureEnabled("homes") || !player.hasPermission(PermissionNodes.SETHOME)) {
            PlatformText.send(player, plugin.msg("no-permission"));
            return;
        }
        if (plugin.settings().isHomeBlockedOnCurrentServer()) {
            PlatformText.send(player, plugin.msg("home-blocked-server"));
            return;
        }
        var loc = player.getLocation();
        if (loc.getWorld() == null) return;
        var home = new HomeRecord(player.getUniqueId().toString(), name, plugin.settings().serverName(),
                plugin.versionAdapter().getWorldName(loc.getWorld()), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        plugin.services().homeService().saveWithinLimit(home, HomeAllowance.forPlayer(player, plugin.settings().homeCreationCap()), replace)
                .whenComplete((saved, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) plugin.getLogger().warning("Could not save a home: " + error.getMessage());
                    if (!player.isOnline()) return;
                    String key = error != null ? "home-save-failed" : saved ? "home-set" : "home-no-empty-slot";
                    PlatformText.send(player, plugin.msg(key, Map.of("name", name)));
                    reopen.run();
                }));
    }
}
