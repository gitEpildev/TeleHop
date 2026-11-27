package com.telehop.paper.command.home;

import com.telehop.paper.platform.PlatformText;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import com.telehop.common.PermissionNodes;
import com.telehop.common.model.HomeRecord;
import com.telehop.paper.NetworkPaperPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.regex.Pattern;

@CommandAlias("%{cmd-sethome}")
public class SetHomeCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;
    private static final int MAX_NAME_LENGTH = 32;
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_ ]+$");

    public SetHomeCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Default
    public void execute(Player player, String name) {
        if (!plugin.isFeatureEnabled("homes")) {
            PlatformText.send(player, plugin.msg("feature-disabled"));
            return;
        }
        if (!plugin.permissionService().has(player, PermissionNodes.SETHOME)) {
            PlatformText.send(player, plugin.msg("no-permission"));
            return;
        }
        if (plugin.settings().isHomeBlockedOnCurrentServer()) {
            PlatformText.send(player, plugin.msg("home-blocked-server"));
            return;
        }

        if (name == null || name.isBlank()) {
            PlatformText.send(player, plugin.msg("home-invalid-name"));
            return;
        }
        if (name.length() > MAX_NAME_LENGTH) {
            PlatformText.send(player, plugin.msg("home-name-too-long", Map.of("max", String.valueOf(MAX_NAME_LENGTH))));
            return;
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            PlatformText.send(player, plugin.msg("home-invalid-name"));
            return;
        }

        int maxSlots = resolveMaxSlots(player);
        String uuid = player.getUniqueId().toString();

        plugin.services().homeService().find(uuid, name).thenAccept(existing -> {
            if (existing.isPresent()) {
                Location loc = player.getLocation();
                HomeRecord home = new HomeRecord(uuid, existing.get().name(),
                        plugin.settings().serverName(), plugin.versionAdapter().getWorldName(loc.getWorld()),
                        loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
                plugin.services().homeService().upsert(home).thenRun(() ->
                        org.bukkit.Bukkit.getScheduler().runTask(plugin, () ->
                                PlatformText.send(player, plugin.msg("home-set", Map.of("name", existing.get().name())))));
                return;
            }

            plugin.services().homeService().countByPlayer(uuid).thenAccept(count -> {
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    if (count >= maxSlots) {
                        PlatformText.send(player, plugin.msg("home-no-empty-slot"));
                        return;
                    }
                    Location loc = player.getLocation();
                    HomeRecord home = new HomeRecord(uuid, name,
                            plugin.settings().serverName(), plugin.versionAdapter().getWorldName(loc.getWorld()),
                            loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
                    plugin.services().homeService().upsert(home).thenRun(() ->
                            org.bukkit.Bukkit.getScheduler().runTask(plugin, () ->
                                    PlatformText.send(player, plugin.msg("home-set", Map.of("name", name)))));
                });
            });
        });
    }

    private int resolveMaxSlots(Player player) {
        for (int i = plugin.settings().homeMaxSlots(); i >= 1; i--) {
            if (player.hasPermission(PermissionNodes.HOME_LIMIT_PREFIX + i)) return i;
        }
        return 0;
    }
}
