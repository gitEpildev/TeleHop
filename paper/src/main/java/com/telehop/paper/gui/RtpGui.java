package com.telehop.paper.gui;

import com.telehop.paper.NetworkPaperPlugin;
import com.telehop.paper.service.PingService;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

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
        List<RegionEntry> entries = new ArrayList<>();
        for (String key : regionKeys) {
            ConfigurationSection guiSection = regions.getConfigurationSection(key + ".gui");
            Material material = parseMaterial(guiSection, Material.GRASS_BLOCK);
            String name = guiSection != null ? guiSection.getString("name", "<green><bold>" + capitalize(key) + "</bold>") : "<green><bold>" + capitalize(key) + "</bold>";
            List<String> baseLore = guiSection != null ? guiSection.getStringList("lore") : List.of();
            List<String> lore = new ArrayList<>(baseLore);
            lore.addAll(buildPingLore(cfg, player, key));

            ItemStack item = buildItem(material, name, lore);
            final String regionKey = key;
            gui.setItem(slot, ItemBuilder.from(item).asGuiItem(click -> openDimension(player, regionKey)));
            entries.add(new RegionEntry(slot, material, name, baseLore, regionKey));
            slot += 2;
        }
        gui.open(player);
        startPingRefresh(player, gui, cfg, entries);
    }

    private void openDimension(Player player, String region) {
        FileConfiguration cfg = loadRtpConfig();
        String title = cfg.getString("rtp.gui.dimension-menu.title", "<gold>Select Dimension</gold>");
        int rows = cfg.getInt("rtp.gui.dimension-menu.rows", 3);

        Gui gui = Gui.gui().title(plugin.messageService().deserialize(title)).rows(Math.max(1, Math.min(6, rows))).disableAllInteractions().create();
        gui.setItem(11, ItemBuilder.from(buildItem(Material.GRASS_BLOCK, "<green><bold>Overworld</bold>",
                        List.of("<gray>Random teleport in the Overworld")))
                .asGuiItem(click -> select(player, region, "overworld")));
        gui.setItem(13, ItemBuilder.from(buildItem(Material.NETHERRACK, "<red><bold>Nether</bold>",
                        List.of("<gray>Random teleport in the Nether")))
                .asGuiItem(click -> select(player, region, "nether")));
        gui.setItem(15, ItemBuilder.from(buildItem(Material.END_STONE, "<light_purple><bold>End</bold>",
                        List.of("<gray>Random teleport in The End")))
                .asGuiItem(click -> select(player, region, "end")));

        boolean hasMultipleRegions = hasMultipleRegions(cfg);
        if (hasMultipleRegions) {
            String backName = plugin.messageService().rawString("rtp-back-button");
            String backLore = plugin.messageService().rawString("rtp-back-lore");
            int backSlot = (rows - 1) * 9;
            gui.setItem(backSlot, ItemBuilder.from(buildItem(Material.SPECTRAL_ARROW, backName, List.of(backLore)))
                    .asGuiItem(click -> openRegion(player)));
        }

        gui.open(player);
    }

    /**
     * Closes the menu before handing the selection over, so the player cannot
     * keep clicking items to restart or stack the warmup countdown.
     */
    private void select(Player player, String region, String dimension) {
        player.closeInventory();
        selectionConsumer.accept(region, dimension);
    }

    // ── ping display ─────────────────────────────────────────────────

    /**
     * Builds the "Ping: ~42ms" hover line for a region item, showing the
     * ping the player will get on the destination server. Only the number
     * is colour-coded (green/yellow/red). Controlled by the
     * {@code rtp.gui.ping} section of rtp.yml.
     */
    private List<String> buildPingLore(FileConfiguration cfg, Player player, String regionKey) {
        if (!cfg.getBoolean("rtp.gui.ping.enabled", true)) {
            return List.of();
        }

        PingService pingService = plugin.services().pingService();
        boolean proxyPing = cfg.getBoolean("rtp.gui.ping.proxy-ping", true);

        String currentServer = plugin.settings().serverName();
        String mapped = plugin.settings().servers().get(regionKey.toLowerCase());
        String targetServer = mapped != null ? mapped : currentServer;

        long playerPing = Math.max(0, player.getPing());
        long estimate;
        if (proxyPing && pingService != null && pingService.hasData()) {
            estimate = pingService.estimateDestinationPing(playerPing, currentServer, targetServer);
        } else {
            estimate = playerPing;
        }

        return List.of("", "<gray>Ping: " + pingColor(estimate) + estimate + "ms");
    }

    private String pingColor(long ping) {
        if (ping < 80) return "<green>";
        if (ping < 150) return "<yellow>";
        return "<red>";
    }

    /**
     * Refreshes the region items twice per second while the menu is open so
     * the ping line stays current, including while the player hovers an
     * item. Stops itself as soon as the menu is closed.
     */
    private void startPingRefresh(Player player, Gui gui, FileConfiguration cfg, List<RegionEntry> entries) {
        if (!cfg.getBoolean("rtp.gui.ping.enabled", true) || entries.isEmpty()) {
            return;
        }
        final BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()
                    || player.getOpenInventory().getTopInventory() != gui.getInventory()) {
                taskHolder[0].cancel();
                return;
            }
            for (RegionEntry entry : entries) {
                List<String> lore = new ArrayList<>(entry.baseLore());
                lore.addAll(buildPingLore(cfg, player, entry.regionKey()));
                gui.updateItem(entry.slot(), buildItem(entry.material(), entry.name(), lore));
            }
        }, 10L, 10L);
    }

    private record RegionEntry(int slot, Material material, String name,
                               List<String> baseLore, String regionKey) {}

    // ── helpers ──────────────────────────────────────────────────────

    private boolean hasMultipleRegions(FileConfiguration cfg) {
        ConfigurationSection regions = cfg.getConfigurationSection("rtp.regions");
        return regions != null && regions.getKeys(false).size() > 1;
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
            // Keep tooltips clean: only the name and lore should show.
            meta.addItemFlags(ItemFlag.values());
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
