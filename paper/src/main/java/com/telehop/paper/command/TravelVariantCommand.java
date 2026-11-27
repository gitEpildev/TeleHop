package com.telehop.paper.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.Default;
import com.telehop.paper.NetworkPaperPlugin;
import com.telehop.paper.service.VariantTravel;
import org.bukkit.entity.Player;

/** Binds a visible label to a stable, locally configured travel action. */
public final class TravelVariantCommand extends BaseCommand {
    private final String id;
    private final NetworkPaperPlugin plugin;

    public TravelVariantCommand(String label, String id, NetworkPaperPlugin plugin) {
        super(label);
        this.id = id;
        this.plugin = plugin;
    }

    @Default
    public void execute(Player player) { VariantTravel.execute(plugin, player, id); }
}
