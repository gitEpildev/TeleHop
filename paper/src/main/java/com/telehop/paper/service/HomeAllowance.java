package com.telehop.paper.service;

import com.telehop.common.PermissionNodes;
import org.bukkit.entity.Player;

/** Resolves the player's current permission context without probing every possible number. */
public final class HomeAllowance {
    private HomeAllowance() {}

    public static int forPlayer(Player player, int cap) {
        if (player.hasPermission(PermissionNodes.HOMES_UNLIMITED)) return cap > 0 ? cap : Integer.MAX_VALUE;
        int limit = player.hasPermission(PermissionNodes.HOME_LIMIT_PREFIX + "1") ? 1 : 0;
        for (var permission : player.getEffectivePermissions()) {
            String node = permission.getPermission();
            if (!permission.getValue() || !node.startsWith(PermissionNodes.HOME_LIMIT_PREFIX)) continue;
            String suffix = node.substring(PermissionNodes.HOME_LIMIT_PREFIX.length());
            try {
                if (!suffix.isEmpty() && Character.isDigit(suffix.charAt(0)) && player.hasPermission(node))
                    limit = Math.max(limit, Integer.parseInt(suffix));
            } catch (NumberFormatException ignored) {
                // Only an exact positive integer is a numbered allowance.
            }
        }
        return cap > 0 ? Math.min(limit, cap) : limit;
    }
}
