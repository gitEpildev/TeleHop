package com.telehop.paper.smoke;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** Companion plugin used only by the packaged Paper server smoke tests. */
public final class CommandProbe extends JavaPlugin {
    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTask(this, () -> {
            boolean custom = Boolean.getBoolean("telehop.test.custom");
            var commands = Bukkit.getCommandMap();
            var telehop = Bukkit.getPluginManager().getPlugin("TeleHop");
            if (telehop == null || !telehop.isEnabled()) throw new AssertionError("TeleHop failed to enable");
            for (String name : custom ? new String[]{"networktp", "ntp", "lobby", "hub"} : new String[]{"tp"}) {
                Command command = commands.getCommand(name);
                if (!(command instanceof PluginIdentifiableCommand owned) || owned.getPlugin() != telehop
                        || commands.getCommand("telehop:" + name) != command) {
                    throw new AssertionError("TeleHop did not register /" + name);
                }
            }
            if ((commands.getCommand("telehop:hub") != null) != custom) throw new AssertionError("Optional hub registration");
            var permissions = Bukkit.getPluginManager();
            if (!permissions.getPermission("telehop.admin").getChildren().containsKey("telehop.admin.homes.visit")
                    || !permissions.getPermission("telehop.tp").getChildren().containsKey("telehop.tp.player.self")) {
                throw new AssertionError("Missing delegated permissions");
            }
            if (custom) {
                Command tp = commands.getCommand("tp");
                Command vanilla = commands.getCommand("minecraft:tp");
                // Paper creates a separate wrapper for each vanilla label.
                if (commands.getCommand("telehop:tp") != null || tp == null || vanilla == null
                        || tp.getClass() != vanilla.getClass() || !"minecraft.command.teleport".equals(tp.getPermission())) {
                    throw new AssertionError("Expected vanilla /tp after rename, found " + tp + " (vanilla: " + vanilla + ")");
                }
            }
            getLogger().info("TeleHop command checks passed (custom=" + custom + ")");
        });
    }
}
