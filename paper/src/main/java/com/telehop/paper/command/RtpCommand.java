package com.telehop.paper.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import com.telehop.paper.NetworkPaperPlugin;
import com.telehop.paper.service.RtpTravel;
import org.bukkit.entity.Player;

/** Opens the configured RTP menu. Variants enter the same execution service. */
@CommandAlias("%{cmd-rtp}")
public class RtpCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public RtpCommand(NetworkPaperPlugin plugin) { this.plugin = plugin; }

    @Default
    public void execute(Player player) { new RtpTravel(plugin).open(player, null); }
}
