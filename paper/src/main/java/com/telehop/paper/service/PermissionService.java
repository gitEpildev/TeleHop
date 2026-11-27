package com.telehop.paper.service;

import com.telehop.common.PermissionNodes;
import com.telehop.common.model.WarpRecord;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Uses native effective permissions on the source and optionally evaluates the destination world with LuckPerms. */
public class PermissionService {
    @FunctionalInterface
    interface WorldDecision {
        Boolean query(Player player, String world, String node);
    }
    private final WorldDecision decisions;

    public PermissionService() { this(null); }
    PermissionService(WorldDecision decisions) { this.decisions = decisions; }

    public static PermissionService create(JavaPlugin plugin) {
        return plugin.getServer().getPluginManager().isPluginEnabled("LuckPerms")
                ? new PermissionService(LuckPermsWorldAccess.create(plugin)) : new PermissionService();
    }

    public boolean has(Player player, String node) { return player.hasPermission(node); }

    /** A specific warp decision takes precedence over the general member grant. */
    public boolean warp(Player player, String name) {
        String specific = PermissionNodes.warpNode(name);
        return player.isPermissionSet(specific) ? player.hasPermission(specific)
                : player.hasPermission(PermissionNodes.WARP) || player.hasPermission(specific);
    }

    public boolean warp(Player player, WarpRecord warp, String backend) {
        if (!warp(player, warp.name())) return false;
        if (!warp.server().equalsIgnoreCase(backend) || !differentWorld(player, warp.world())) return true;
        Boolean individual = decisions.query(player, warp.world(), PermissionNodes.warpNode(warp.name()));
        if (individual != null) return individual;
        Boolean general = decisions.query(player, warp.world(), PermissionNodes.WARP);
        // The bundled general warp permission defaults to everyone unless the destination context overrides it.
        return general == null || general;
    }

    public boolean destination(Player player, String node, boolean restricted) {
        return player.isPermissionSet(node) ? player.hasPermission(node) : !restricted;
    }

    /** Evaluate the target world without changing or teleporting the player to obtain that context. */
    public boolean inWorld(Player player, String world, String node, boolean whenUnset) {
        if (!differentWorld(player, world)) return destination(player, node, !whenUnset);
        Boolean result = decisions.query(player, world, node);
        return result == null ? whenUnset : result;
    }

    private boolean differentWorld(Player player, String world) {
        return decisions != null && !player.getWorld().getName().equalsIgnoreCase(world);
    }
}
