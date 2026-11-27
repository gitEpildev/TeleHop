package com.telehop.paper.service;

import net.luckperms.api.LuckPerms;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/** Loaded only on backends with LuckPerms. Retains server, group and other active context dimensions. */
final class LuckPermsWorldAccess {
    private LuckPermsWorldAccess() {}

    static PermissionService.WorldDecision create(JavaPlugin plugin) {
        var api = plugin.getServer().getServicesManager().load(LuckPerms.class);
        if (api == null) throw new IllegalStateException("LuckPerms is enabled but its permission service is unavailable");
        var adapter = api.getPlayerAdapter(Player.class);
        return (player, world, node) -> {
            var current = adapter.getQueryOptions(player);
            var context = current.context().mutableCopy();
            context.removeAll("world");
            context.add("world", world.toLowerCase(Locale.ROOT));
            var options = current.toBuilder().context(context.immutableCopy()).build();
            var decision = adapter.getUser(player).getCachedData().getPermissionData(options).checkPermission(node);
            return switch (decision) { case TRUE -> Boolean.TRUE; case FALSE -> Boolean.FALSE; case UNDEFINED -> null; };
        };
    }
}
