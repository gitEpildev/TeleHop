package com.telehop.paper.service;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedPermissionData;
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
                CachedPermissionData permissions = user.getCachedData().getPermissionData();
                if (permissions.checkPermission(node).asBoolean()) {
                    return true;
                }
            }
        }
        return player.isOp() || player.hasPermission(node);
    }
}
