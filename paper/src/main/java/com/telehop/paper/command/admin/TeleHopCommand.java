package com.telehop.paper.command.admin;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import com.telehop.paper.NetworkPaperPlugin;
import org.bukkit.command.CommandSender;

import java.util.Map;

@CommandAlias("telehop")
public class TeleHopCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public TeleHopCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Subcommand("reload")
    @CommandPermission("telehop.admin")
    @Description("Reload TeleHop configuration and messages")
    public void reload(CommandSender sender) {
        plugin.reload();
        sender.sendMessage(plugin.msg("reload-success"));
    }

    @Subcommand("version|ver")
    @Description("Show TeleHop version")
    public void version(CommandSender sender) {
        String ver = plugin.getDescription().getVersion();
        sender.sendMessage(plugin.messageService().rawFormat("version-info", Map.of("version", ver)));
    }

    @Subcommand("perms|permissions")
    @CommandPermission("telehop.admin")
    @Description("List all TeleHop permission nodes")
    public void perms(CommandSender sender) {
        String[] keys = {
            "perms-header",
            "perms-player-header", "perms-spawn", "perms-rtp", "perms-warp", "perms-pwarp",
            "perms-tpa", "perms-tpahere", "perms-tpa-accept", "perms-tpa-deny", "perms-tpa-cancel",
            "perms-per-warp-header", "perms-warp-access",
            "perms-warp-limits-header", "perms-warps-number", "perms-warps-unlimited",
            "perms-admin-header", "perms-admin", "perms-tp", "perms-tphere",
            "perms-bypass-header", "perms-rtp-bypass", "perms-rtp-bypass-delay", "perms-tpa-bypass"
        };
        for (String key : keys) {
            sender.sendMessage(plugin.messageService().raw(key));
        }
    }

    @Subcommand("help")
    @Default
    @CatchUnknown
    public void help(CommandSender sender) {
        String[] keys = {
            "help-header",
            "help-general", "help-spawn", "help-rtp",
            "help-tpa-header", "help-tpa", "help-tpahere", "help-tpaaccept", "help-tpadeny", "help-tpacancel",
            "help-warps-header", "help-warp", "help-setwarp", "help-delwarp", "help-warps-list",
            "help-pwarps-header", "help-pwarp-set", "help-pwarp-del", "help-pwarp-list",
            "help-pwarp-tp", "help-pwarp-tp-other", "help-pwarp-public",
            "help-admin-header", "help-tp", "help-tphere", "help-listwarps",
            "help-forcedelwarp", "help-forcedelwarp-player",
            "help-telehop-reload", "help-telehop-version", "help-telehop-perms"
        };
        for (String key : keys) {
            sender.sendMessage(plugin.messageService().raw(key));
        }
    }
}
