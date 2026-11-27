package com.telehop.paper.command.admin;

import com.telehop.paper.platform.PlatformText;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import com.telehop.common.PermissionNodes;
import com.telehop.paper.NetworkPaperPlugin;
import org.bukkit.command.CommandSender;

import java.util.Map;

@CommandAlias("%{cmd-telehop}")
public class TeleHopCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public TeleHopCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Subcommand("config validate")
    @CommandPermission(PermissionNodes.ADMIN_CONFIG_VALIDATE)
    public void validateConfiguration(CommandSender sender) {
        try {
            com.telehop.paper.Bootstrap.validateConfiguration(plugin);
            com.telehop.paper.platform.PlatformText.send(sender, plugin.msg("config-valid"));
        } catch (Exception invalid) {
            plugin.getLogger().warning("Configuration validation failed: " + invalid.getMessage());
            com.telehop.paper.platform.PlatformText.send(sender, plugin.msg("reload-invalid"));
        }
    }

    @Subcommand("platform info")
    @CommandPermission(PermissionNodes.ADMIN_PLATFORM_INFO)
    public void platformInfo(CommandSender sender) {
        com.telehop.paper.platform.PlatformText.send(sender, plugin.msg("platform-info",
                Map.of("platform", plugin.platform().description())));
    }

    @Subcommand("migration status")
    @CommandPermission(PermissionNodes.ADMIN_MIGRATION_STATUS)
    public void migrationStatus(CommandSender sender) {
        var data = plugin.getDataFolder().toPath();
        try {
            var state = com.telehop.common.config.ConfigYaml.read(data.resolve("migrations/state.yml"));
            com.telehop.paper.platform.PlatformText.send(sender, plugin.msg("migration-state",
                    Map.of("mode", plugin.configuration().mode().name(), "schema", String.valueOf(state.get("schema")),
                            "migration", String.valueOf(state.get("migration")))));
        } catch (java.io.IOException | IllegalArgumentException error) {
            com.telehop.paper.platform.PlatformText.send(sender, plugin.msg("reload-invalid"));
        }
    }

    @Subcommand("diagnostics")
    @CommandPermission(PermissionNodes.ADMIN_DIAGNOSTICS)
    public void diagnostics(CommandSender sender) {
        var breaker = plugin.services().databaseManager().circuitBreaker();
        com.telehop.paper.platform.PlatformText.send(sender, plugin.msg("network-diagnostics", Map.of(
                "mode", plugin.configuration().mode().name(), "server", plugin.settings().serverName(),
                "database", breaker.state().name(), "failures", Integer.toString(breaker.consecutiveFailures()))));
    }

    @Subcommand("reload")
    @CommandPermission(PermissionNodes.ADMIN_RELOAD)
    @Description("Reload TeleHop configuration and messages")
    public void reload(CommandSender sender) {
        try {
            plugin.reload().whenComplete((ignored, error) -> {
                if (error != null) plugin.getLogger().warning("Reload rejected: " + error.getMessage());
                PlatformText.send(sender, plugin.msg(error == null ? "reload-success" : "reload-invalid"));
                if (error == null && plugin.settings().isFeatureEnabled("hub") != plugin.services().spawns().hubCommand()) {
                    PlatformText.send(sender, plugin.msg("hub-restart-required"));
                }
            });
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning(ex.getMessage());
            PlatformText.send(sender, plugin.msg("reload-invalid"));
        }
    }

    @Subcommand("reload config")
    @CommandPermission(PermissionNodes.ADMIN_RELOAD)
    public void reloadConfig(CommandSender sender) { reload(sender); }

    @Subcommand("reload messages")
    @CommandPermission(PermissionNodes.ADMIN_RELOAD)
    public void reloadMessages(CommandSender sender) {
        try {
            com.telehop.paper.Bootstrap.reloadMessages(plugin);
            PlatformText.send(sender, plugin.msg("messages-reloaded"));
        } catch (IllegalArgumentException error) {
            plugin.getLogger().warning(error.getMessage());
            PlatformText.send(sender, plugin.msg("reload-invalid"));
        }
    }

    @Subcommand("commands list")
    @CommandPermission(PermissionNodes.ADMIN_COMMANDS_LIST)
    public void commands(CommandSender sender) {
        plugin.commandNames().labels().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                PlatformText.send(sender, plugin.msg("command-line", Map.of("id", entry.getKey(),
                        "names", "/" + String.join(", /", entry.getValue())))));
    }

    @Subcommand("commands validate")
    @CommandPermission(PermissionNodes.ADMIN_COMMANDS_VALIDATE)
    public void validateCommands(CommandSender sender) { commandRules(sender, false); }

    @Subcommand("reload commands")
    @CommandPermission(PermissionNodes.ADMIN_COMMANDS_RELOAD)
    public void reloadCommands(CommandSender sender) { commandRules(sender, true); }

    private void commandRules(CommandSender sender, boolean apply) {
        try {
            com.telehop.paper.Bootstrap.commandRules(plugin, apply);
            PlatformText.send(sender, plugin.msg(apply ? "reload-success" : "config-valid"));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning(ex.getMessage());
            PlatformText.send(sender, plugin.msg("reload-invalid"));
        }
    }

    @Subcommand("version|ver")
    @Description("Show TeleHop version")
    public void version(CommandSender sender) {
        String ver = plugin.getDescription().getVersion();
        PlatformText.send(sender, plugin.messageService().rawFormat("version-info", Map.of("version", ver)));
    }

    @Subcommand("perms|permissions")
    @CommandPermission(PermissionNodes.ADMIN_PERMISSIONS)
    @Description("List all TeleHop permission nodes")
    public void perms(CommandSender sender) {
        String[] keys = {
            "perms-header",
            "perms-player-header", "perms-spawn", "perms-spawn-routes", "perms-rtp", "perms-warp", "perms-pwarp",
            "perms-tpa", "perms-tpahere", "perms-tpa-accept", "perms-tpa-deny", "perms-tpa-cancel",
            "perms-tpa-toggle",
            "perms-homes-header", "perms-homes", "perms-sethome", "perms-delhome", "perms-homes-limit",
            "perms-lastloc",
            "perms-back", "perms-back-death",
            "perms-per-warp-header", "perms-warp-access",
            "perms-warp-limits-header", "perms-warps-number", "perms-warps-unlimited",
            "perms-admin-header", "perms-admin", "perms-tp", "perms-tp-coordinates", "perms-tphere",
            "perms-bypass-header", "perms-rtp-bypass", "perms-rtp-bypass-delay", "perms-tpa-bypass"
        };
        for (String key : keys) {
            PlatformText.send(sender, plugin.messageService().raw(key));
        }
        for (String node : java.util.List.of(
                PermissionNodes.SPAWN_NAMED, PermissionNodes.HUB, PermissionNodes.TP_PLAYER_SELF,
                PermissionNodes.TP_PLAYER_OTHERS, PermissionNodes.ADMIN_SPAWN_LIST, PermissionNodes.ADMIN_SPAWN_SET,
                PermissionNodes.ADMIN_SPAWN_DELETE, PermissionNodes.ADMIN_HOMES_LIST, PermissionNodes.ADMIN_HOMES_VISIT,
                PermissionNodes.ADMIN_HOMES_SET, PermissionNodes.ADMIN_HOMES_DELETE, PermissionNodes.ADMIN_LASTLOCATION_VIEW,
                PermissionNodes.ADMIN_LASTLOCATION_VISIT, PermissionNodes.ADMIN_LASTLOCATION_CLEAR, PermissionNodes.ADMIN_WARPS_SET,
                PermissionNodes.ADMIN_WARPS_LIST, PermissionNodes.ADMIN_WARPS_DELETE, PermissionNodes.ADMIN_PLAYERWARPS_DELETE,
                PermissionNodes.ADMIN_PLAYERINFO, PermissionNodes.ADMIN_RELOAD, PermissionNodes.ADMIN_COMMANDS_LIST,
                PermissionNodes.ADMIN_COMMANDS_VALIDATE, PermissionNodes.ADMIN_COMMANDS_RELOAD,
                PermissionNodes.ADMIN_CONFIG_VALIDATE, PermissionNodes.ADMIN_PLATFORM_INFO, PermissionNodes.ADMIN_DIAGNOSTICS,
                PermissionNodes.ADMIN_MIGRATION_STATUS, PermissionNodes.COMMAND_PREFIX + "{id}", PermissionNodes.rtpDestination("region", "{id}"),
                PermissionNodes.rtpDestination("server", "{id}"), PermissionNodes.rtpDestination("world", "{id}"),
                PermissionNodes.rtpDestination("dimension", "{id}"), PermissionNodes.ADMIN_PERMISSIONS)) {
            PlatformText.send(sender, net.kyori.adventure.text.Component.text("    " + node));
        }
    }

    @Subcommand("help")
    @Default
    @CatchUnknown
    public void help(CommandSender sender) {
        String[] keys = {
            "help-header",
            "help-general", "help-named-spawn", "help-hub", "help-rtp",
            "help-tpa-header", "help-tpa", "help-tpahere", "help-tpaaccept", "help-tpadeny", "help-tpacancel",
            "help-tpatoggle",
            "help-homes-header", "help-home", "help-sethome", "help-delhome", "help-lastloc",
            "help-back-header", "help-back", "help-backdeath",
            "help-warps-header", "help-warp", "help-setwarp", "help-delwarp", "help-warps-list",
            "help-pwarps-header", "help-pwarp-set", "help-pwarp-del", "help-pwarp-list",
            "help-pwarp-tp", "help-pwarp-tp-other", "help-pwarp-public",
            "help-admin-header", "help-tp", "help-tphere", "help-listwarps",
            "help-forcedelwarp", "help-forcedelwarp-player",
            "help-forcedelhome", "help-listhomes", "help-forcesethome",
            "help-forcelastloc", "help-playerinfo",
            "help-telehop-reload", "help-travel-commands", "help-config-tools", "help-telehop-version", "help-telehop-perms"
        };
        for (String key : keys) {
            if (key.equals("help-hub") && !plugin.services().spawns().hubCommand()) continue;
            PlatformText.send(sender, plugin.messageService().raw(key));
        }
        if (sender instanceof org.bukkit.entity.Player player) {
            plugin.commandNames().variants().values().stream()
                    .filter(variant -> com.telehop.paper.service.VariantTravel.visible(plugin, player, variant))
                    .sorted(java.util.Comparator.comparing(com.telehop.paper.config.TravelVariant::id))
                    .forEach(variant -> PlatformText.send(player, plugin.msg("command-line", Map.of("id", variant.id(),
                            "names", "/" + String.join(", /", variant.labels())))));
        }
    }

    @Subcommand("spawn list")
    @CommandPermission(PermissionNodes.ADMIN_SPAWN_LIST)
    public void listSpawn(CommandSender sender) {
        plugin.services().spawns().list(sender);
    }

    @Subcommand("spawn set")
    @CommandPermission(PermissionNodes.ADMIN_SPAWN_SET)
    public void setSpawn(org.bukkit.entity.Player sender, String id, String access) {
        plugin.services().spawns().save(sender, id, access);
    }

    @Subcommand("spawn setdefault")
    @CommandPermission(PermissionNodes.ADMIN_SPAWN_SET)
    public void setdefaultSpawn(org.bukkit.entity.Player sender) {
        plugin.services().spawns().saveDefault(sender);
    }

    @Subcommand("spawn delete")
    @CommandPermission(PermissionNodes.ADMIN_SPAWN_DELETE)
    public void deleteSpawn(CommandSender sender, String id, @Optional String confirmation) {
        plugin.services().spawns().delete(sender, id, confirmation);
    }
}
