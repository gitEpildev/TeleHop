package com.telehop.paper.service;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;

public class PermissionService {
    private final LuckPerms luckPerms;

    public PermissionService() {
        LuckPerms lp;
        try {
            lp = LuckPermsProvider.get();
        } catch (IllegalStateException ex) {
            lp = null;
        }
        this.luckPerms = lp;
    }

    public boolean has(Player player, String node) {
        if (luckPerms != null) {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                return user.getCachedData().getPermissionData()
                        .checkPermission(node).asBoolean();
            }
        }
        return player.hasPermission(node);
    }

    /**
     * Returns true only if the exact permission node is explicitly set on the
     * player or one of their groups — wildcard resolution ({@code *}) is ignored.
     * Use this for opt-out permissions like bypass nodes where you don't want
     * {@code *} to automatically grant them.
     */
    public boolean hasExplicit(Player player, String node) {
        if (luckPerms != null) {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                return user.getNodes().stream()
                        .anyMatch(n -> n.getKey().equalsIgnoreCase(node) && n.getValue());
            }
        }
        return player.isPermissionSet(node) && player.hasPermission(node);
    }
}
