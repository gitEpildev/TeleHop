package com.telehop.paper.command.admin;

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

    @Subcommand("reload")
    @CommandPermission(PermissionNodes.ADMIN_RELOAD)
    @Description("Reload TeleHop configuration and messages")
    public void reload(CommandSender sender) {
        try {
            plugin.reload().whenComplete((ignored, error) -> {
                if (error != null) plugin.getLogger().warning("Reload rejected: " + error.getMessage());
                sender.sendMessage(plugin.msg(error == null ? "reload-success" : "reload-invalid"));
            });
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning(ex.getMessage());
            sender.sendMessage(plugin.msg("reload-invalid"));
        }
    }

    @Subcommand("version|ver")
    @Description("Show TeleHop version")
    public void version(CommandSender sender) {
        String ver = plugin.getDescription().getVersion();
        sender.sendMessage(plugin.messageService().rawFormat("version-info", Map.of("version", ver)));
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
            sender.sendMessage(plugin.messageService().raw(key));
        }
        for (String node : java.util.List.of(
                PermissionNodes.SPAWN_NAMED, PermissionNodes.HUB, PermissionNodes.TP_PLAYER_SELF,
                PermissionNodes.TP_PLAYER_OTHERS, PermissionNodes.ADMIN_SPAWN_LIST, PermissionNodes.ADMIN_SPAWN_SET,
                PermissionNodes.ADMIN_SPAWN_DELETE, PermissionNodes.ADMIN_HOMES_LIST, PermissionNodes.ADMIN_HOMES_VISIT,
                PermissionNodes.ADMIN_HOMES_SET, PermissionNodes.ADMIN_HOMES_DELETE, PermissionNodes.ADMIN_LASTLOCATION_VIEW,
                PermissionNodes.ADMIN_LASTLOCATION_VISIT, PermissionNodes.ADMIN_LASTLOCATION_CLEAR, PermissionNodes.ADMIN_WARPS_SET,
                PermissionNodes.ADMIN_WARPS_LIST, PermissionNodes.ADMIN_WARPS_DELETE, PermissionNodes.ADMIN_PLAYERWARPS_DELETE,
                PermissionNodes.ADMIN_PLAYERINFO, PermissionNodes.ADMIN_RELOAD, PermissionNodes.ADMIN_PERMISSIONS)) {
            sender.sendMessage(net.kyori.adventure.text.Component.text("    " + node));
        }
    }

    @Subcommand("help")
    @Default
    @CatchUnknown
    public void help(CommandSender sender) {
        String[] keys = {
            "help-header",
            "help-general", "help-spawn-routes", "help-rtp",
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
            "help-telehop-reload", "help-telehop-version", "help-telehop-perms"
        };
        for (String key : keys) {
            sender.sendMessage(plugin.messageService().raw(key));
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
