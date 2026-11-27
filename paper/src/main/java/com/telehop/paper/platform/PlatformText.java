package com.telehop.paper.platform;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.json.JSONOptions;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Renders plugin-owned Adventure components using the available backend chat API. */
public final class PlatformText {
    private static final boolean SPIGOT_CHAT = PlatformCapabilities.hasSpigotChat();
    private static GsonComponentSerializer serializer = GsonComponentSerializer.gson();
    private static JavaPlugin plugin;

    private PlatformText() {}

    @SuppressWarnings("deprecation")
    public static void initialise(JavaPlugin owner) {
        plugin = owner;
        serializer = GsonComponentSerializer.builder().options(
                JSONOptions.byDataVersion().at(owner.getServer().getUnsafe().getDataVersion())).build();
    }

    public static void shutdown() {
        plugin = null;
    }

    public static String legacy(Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

    public static void send(CommandSender sender, Component component) {
        if (sender instanceof Player player && SPIGOT_CHAT) {
            player.spigot().sendMessage(ComponentSerializer.parse(serializer.serialize(component)));
        } else if (sender instanceof Player player && interactive(component)) {
            // Original Bukkit has no component chat method. Vanilla accepts a player-only selector and a typed text component.
            Runnable deliver = () -> {
                if (!player.isOnline()) return;
                String name = player.getName().replace("\\", "\\\\").replace("\"", "\\\"");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        "minecraft:tellraw @a[name=\"" + name + "\",limit=1] " + serializer.serialize(component));
            };
            if (Bukkit.isPrimaryThread()) deliver.run();
            else Bukkit.getScheduler().runTask(plugin, deliver);
        } else {
            sender.sendMessage(legacy(component));
        }
    }

    private static boolean interactive(Component component) {
        return component.clickEvent() != null || component.hoverEvent() != null
                || component.children().stream().anyMatch(PlatformText::interactive);
    }
}
