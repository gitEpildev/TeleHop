package com.telehop.paper.service;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class PermissionService {
    private final LuckPerms luckPerms;

    public PermissionService() {
        LuckPerms lp = null;
        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                lp = LuckPermsProvider.get();
            } catch (IllegalStateException ignored) {
                // The provider is unavailable during startup. Fall back to Bukkit permissions.
            }
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

}
