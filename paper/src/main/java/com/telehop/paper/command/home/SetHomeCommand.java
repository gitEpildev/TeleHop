package com.telehop.paper.command.home;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import com.telehop.common.PermissionNodes;
import com.telehop.common.model.HomeRecord;
import com.telehop.paper.NetworkPaperPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;

@CommandAlias("sethome")
public class SetHomeCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public SetHomeCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Default
    public void execute(Player player) {
        if (!plugin.isFeatureEnabled("homes")) {
            player.sendMessage(plugin.msg("feature-disabled"));
            return;
        }
        if (!plugin.permissionService().has(player, PermissionNodes.HOMES)) {
            player.sendMessage(plugin.msg("no-permission"));
            return;
        }
        if (plugin.settings().isHomeBlockedOnCurrentServer()) {
            player.sendMessage(plugin.msg("home-blocked-server"));
            return;
        }

        int maxSlots = resolveMaxSlots(player);
        String uuid = player.getUniqueId().toString();

        plugin.services().homeService().listByPlayer(uuid).thenAccept(homes -> {
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                int nextSlot = -1;
                for (int i = 1; i <= maxSlots; i++) {
                    final int slot = i;
                    if (homes.stream().noneMatch(h -> h.slot() == slot)) {
                        nextSlot = i;
                        break;
                    }
                }
                if (nextSlot == -1) {
                    player.sendMessage(plugin.msg("home-no-empty-slot"));
                    return;
                }

                Location loc = player.getLocation();
                HomeRecord home = new HomeRecord(uuid, nextSlot,
                        plugin.settings().serverName(), loc.getWorld().getName(),
                        loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
                final int finalSlot = nextSlot;
                plugin.services().homeService().upsert(home).thenRun(() ->
                        org.bukkit.Bukkit.getScheduler().runTask(plugin, () ->
                                player.sendMessage(plugin.msg("home-set", Map.of("slot", String.valueOf(finalSlot))))));
            });
        });
    }

    private int resolveMaxSlots(Player player) {
        for (int i = plugin.settings().homeMaxSlots(); i >= 1; i--) {
            if (player.hasPermission("telehop.homes." + i)) return i;
        }
        return 0;
    }
}
