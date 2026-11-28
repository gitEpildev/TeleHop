package com.telehop.paper.command.home;

import com.telehop.paper.platform.PlatformText;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import com.telehop.common.PermissionNodes;
import com.telehop.paper.NetworkPaperPlugin;
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

        com.telehop.paper.service.HomeCreation.save(plugin, player, name, true, () -> {});
    }
}
