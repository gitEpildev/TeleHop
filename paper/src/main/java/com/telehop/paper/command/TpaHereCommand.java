package com.telehop.paper.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import com.telehop.common.model.TpaType;
import com.telehop.paper.NetworkPaperPlugin;
import org.bukkit.entity.Player;

@CommandAlias("tpahere")
public class TpaHereCommand extends BaseCommand {
    private final TpaCommand delegate;

    public TpaHereCommand(NetworkPaperPlugin plugin) {
        this.delegate = new TpaCommand(plugin);
    }

    @Default
    @co.aikar.commands.annotation.CommandCompletion("@networkplayers")
    public void execute(Player sender, String targetName) {
        delegate.createRequest(sender, targetName, TpaType.TPA_HERE);
    }
}
