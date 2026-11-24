package com.telehop.paper.service;

import org.bukkit.entity.Player;

/** Uses the effective Bukkit permissions, including LuckPerms contexts and inherited grants. */
public class PermissionService {
    public boolean has(Player player, String node) {
        return player.hasPermission(node);
    }

    public boolean destination(Player player, String node, boolean restricted) {
        return player.isPermissionSet(node) ? player.hasPermission(node) : !restricted;
    }
}
