package com.telehop.paper.gui;

import com.telehop.paper.NetworkPaperPlugin;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

public class RtpGui {
    private final NetworkPaperPlugin plugin;
    private final BiConsumer<String, String> selectionConsumer;

    public RtpGui(NetworkPaperPlugin plugin, BiConsumer<String, String> selectionConsumer) {
        this.plugin = plugin;
        this.selectionConsumer = selectionConsumer;
    }

    public void openRegion(Player player) {
        FileConfiguration cfg = loadRtpConfig();
        ConfigurationSection regions = cfg.getConfigurationSection("rtp.regions");
        if (regions == null || regions.getKeys(false).isEmpty()) {
            selectionConsumer.accept("default", "overworld");
            return;
        }
        Set<String> regionKeys = regions.getKeys(false);
        if (regionKeys.size() == 1) {
            openDimension(player, regionKeys.iterator().next());
            return;
        }

        String title = cfg.getString("rtp.gui.region-menu.title", "<dark_purple>Select Region</dark_purple>");
        int rows = cfg.getInt("rtp.gui.region-menu.rows", 3);
        Gui gui = Gui.gui().title(plugin.messageService().deserialize(title)).rows(Math.max(1, Math.min(6, rows))).disableAllInteractions().create();

        int slot = centerStartSlot(rows, regionKeys.size());
        for (String key : regionKeys) {
            ConfigurationSection guiSection = regions.getConfigurationSection(key + ".gui");
            Material material = parseMaterial(guiSection, Material.GRASS_BLOCK);
            String name = guiSection != null ? guiSection.getString("name", "<green><bold>" + capitalize(key) + "</bold>") : "<green><bold>" + capitalize(key) + "</bold>";
            List<String> lore = guiSection != null ? guiSection.getStringList("lore") : List.of();

            ItemStack item = buildItem(material, name, lore);
            final String regionKey = key;
            gui.setItem(slot, ItemBuilder.from(item).asGuiItem(click -> openDimension(player, regionKey)));
            slot += 2;
        }
        gui.open(player);
    }

    private void openDimension(Player player, String region) {
        FileConfiguration cfg = loadRtpConfig();
        String title = cfg.getString("rtp.gui.dimension-menu.title", "<gold>Select Dimension</gold>");
        int rows = cfg.getInt("rtp.gui.dimension-menu.rows", 3);

        Gui gui = Gui.gui().title(plugin.messageService().deserialize(title)).rows(Math.max(1, Math.min(6, rows))).disableAllInteractions().create();
        gui.setItem(11, ItemBuilder.from(buildItem(Material.GRASS_BLOCK, "<green><bold>Overworld</bold>",
                        List.of("<gray>Random teleport in the Overworld")))
                .asGuiItem(click -> selectionConsumer.accept(region, "overworld")));
        gui.setItem(13, ItemBuilder.from(buildItem(Material.NETHERRACK, "<red><bold>Nether</bold>",
                        List.of("<gray>Random teleport in the Nether")))
                .asGuiItem(click -> selectionConsumer.accept(region, "nether")));
        gui.setItem(15, ItemBuilder.from(buildItem(Material.END_STONE, "<light_purple><bold>End</bold>",
                        List.of("<gray>Random teleport in The End")))
                .asGuiItem(click -> selectionConsumer.accept(region, "end")));
        gui.open(player);
    }

    private FileConfiguration loadRtpConfig() {
        File rtpFile = new File(plugin.getDataFolder(), "config/rtp.yml");
        if (rtpFile.exists()) return YamlConfiguration.loadConfiguration(rtpFile);
        return plugin.getConfig();
    }

    private ItemStack buildItem(Material material, String name, List<String> loreRaw) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(plugin.messageService().deserialize(name));
            if (!loreRaw.isEmpty()) {
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                for (String line : loreRaw) {
                    lore.add(plugin.messageService().deserialize(line));
                }
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private Material parseMaterial(ConfigurationSection section, Material fallback) {
        if (section == null) return fallback;
        String raw = section.getString("material", fallback.name());
        Material m = Material.matchMaterial(raw);
        return m != null ? m : fallback;
    }

    private int centerStartSlot(int rows, int itemCount) {
        int totalSlots = rows * 9;
        int middleRow = rows / 2;
        int widthNeeded = itemCount * 2 - 1;
        int startCol = Math.max(0, (9 - widthNeeded) / 2);
        return middleRow * 9 + startCol;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
