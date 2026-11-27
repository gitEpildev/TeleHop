package com.telehop.paper.gui;

import com.telehop.paper.NetworkPaperPlugin;
import com.telehop.paper.platform.PlatformText;
import com.telehop.paper.service.PingService;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import com.telehop.paper.config.RtpPolicy;
import com.telehop.paper.config.TravelVariant;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

public class RtpGui {
    private final NetworkPaperPlugin plugin;
    private final BiConsumer<String, String> selectionConsumer;

    private final TravelVariant variant;

    public RtpGui(NetworkPaperPlugin plugin, TravelVariant variant, BiConsumer<String, String> selectionConsumer) {
        this.plugin = plugin;
        this.variant = variant;
        this.selectionConsumer = selectionConsumer;
    }

    private RtpPolicy policy() { return plugin.commandNames().rtp(); }

    private List<String> dimensions(Player player, String region) {
        return policy().eligibleDimensions(player, variant, region, plugin.permissionService());
    }

    private List<String> regions(Player player) {
        List<String> options = variant != null && variant.region() != null ? List.of(variant.region())
                : variant != null || policy().regions().isEmpty() ? List.of("")
                : List.copyOf(policy().regions().keySet());
        return options.stream().filter(key -> !dimensions(player, key).isEmpty()).toList();
    }

    public void openRegion(Player player) { openRegion(player, 0); }

    private void openRegion(Player player, int requestedPage) {
        var available = regions(player);
        var page = Page.forItems(available.size(), policy().regionMenu().rows(), false, requestedPage);
        var choices = available.subList(page.start(), page.end());
        if (choices.isEmpty()) { PlatformText.send(player, plugin.msg("no-permission")); return; }
        if (available.size() == 1) {
            if (variant == null && policy().regions().isEmpty() && !policy().selectDimensionWithoutRegion()) {
                String dimension = policy().defaultDimension();
                if (dimensions(player, "").contains(dimension)) selectionConsumer.accept("", dimension);
                else PlatformText.send(player, plugin.msg("no-permission"));
            } else openDimension(player, choices.getFirst());
            return;
        }
        var menu = policy().regionMenu();
        int rows = page.rows();
        Gui gui = Gui.gui().title(plugin.messageService().deserialize(menu.title())).rows(rows).disableAllInteractions().create();
        var entries = new ArrayList<RegionEntry>();
        for (int index = 0; index < choices.size(); index++) {
            String key = choices.get(index);
            var region = policy().regions().get(key);
            Material material = icon(region.material());
            var lore = new ArrayList<>(region.lore());
            lore.addAll(buildPingLore(player, key));
            int slot = slot(rows, choices.size(), index);
            gui.setItem(slot, ItemBuilder.from(buildItem(material, region.title(), lore)).asGuiItem(click -> openDimension(player, key)));
            entries.add(new RegionEntry(slot, material, region.title(), region.lore(), key));
        }
        navigation(gui, page, index -> openRegion(player, index));
        gui.open(player);
        startPingRefresh(player, gui, entries);
    }

    private void openDimension(Player player, String region) { openDimension(player, region, 0); }

    private void openDimension(Player player, String region, int requestedPage) {
        var available = dimensions(player, region);
        boolean back = regions(player).size() > 1;
        var page = Page.forItems(available.size(), policy().dimensionMenu().rows(), back, requestedPage);
        var choices = available.subList(page.start(), page.end());
        if (choices.isEmpty()) { player.closeInventory(); PlatformText.send(player, plugin.msg("no-permission")); return; }
        var menu = policy().dimensionMenu();
        int rows = page.rows();
        Gui gui = Gui.gui().title(plugin.messageService().deserialize(menu.title())).rows(rows).disableAllInteractions().create();
        for (int index = 0; index < choices.size(); index++) {
            String dimension = choices.get(index);
            var item = policy().dimensionItem(dimension, dimension.isEmpty() ? variant.world() : dimension);
            Material icon = icon(item.material());
            int slot = slot(rows, choices.size(), index);
            // Keep the last slot available when the menu also contains a back button.
            if (back && slot == rows * 9 - 1) slot = index;
            gui.setItem(slot, ItemBuilder.from(buildItem(icon, item.title(), item.lore())).asGuiItem(click -> select(player, region, dimension)));
        }
        if (back) {
            gui.setItem(page.paged() ? rows * 9 - 5 : rows * 9 - 1, ItemBuilder.from(buildItem(Material.SPECTRAL_ARROW,
                    plugin.messageService().rawString("rtp-back-button"), List.of(plugin.messageService().rawString("rtp-back-lore"))))
                    .asGuiItem(click -> openRegion(player)));
        }
        navigation(gui, page, index -> openDimension(player, region, index));
        gui.open(player);
    }

    private static Material icon(String configured) {
        var material = Material.matchMaterial(configured);
        return material == null || !material.isItem() || material.isAir() ? Material.GRASS_BLOCK : material;
    }

    record Page(int start, int end, int rows, int index, int last) {
        static Page forItems(int count, int minimumRows, boolean back, int requested) {
            int needed = count + (back ? 1 : 0);
            if (needed <= 54) return new Page(0, count, Math.max(minimumRows, (needed + 8) / 9), 0, 0);
            // Reserve the bottom row for navigation on inventories larger than a double chest.
            int last = (count - 1) / 45;
            int index = Math.clamp(requested, 0, last);
            return new Page(45 * index, Math.min(count, 45 * (index + 1)), 6, index, last);
        }
        boolean paged() { return last > 0; }
    }

    private void navigation(Gui gui, Page page, IntConsumer change) {
        if (page.index() > 0) {
            gui.setItem(45, ItemBuilder.from(buildItem(Material.ARROW, plugin.messageService().rawString("rtp-previous-page"), List.of()))
                    .asGuiItem(click -> change.accept(page.index() - 1)));
        }
        if (page.index() < page.last()) {
            gui.setItem(53, ItemBuilder.from(buildItem(Material.ARROW, plugin.messageService().rawString("rtp-next-page"), List.of()))
                    .asGuiItem(click -> change.accept(page.index() + 1)));
        }
    }

    private static int slot(int rows, int count, int index) {
        return count <= 4 ? (rows / 2) * 9 + 4 - count + 1 + index * 2 : index;
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
    private List<String> buildPingLore(Player player, String regionKey) {
        if (!policy().ping()) {
            return List.of();
        }

        PingService pingService = plugin.services().pingService();
        boolean proxyPing = policy().proxyPing();

        String currentServer = plugin.settings().serverName();
        String targetServer = policy().server(regionKey);

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
    private void startPingRefresh(Player player, Gui gui, List<RegionEntry> entries) {
        if (!policy().ping() || entries.isEmpty()) {
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
                lore.addAll(buildPingLore(player, entry.regionKey()));
                gui.updateItem(entry.slot(), buildItem(entry.material(), entry.name(), lore));
            }
        }, 10L, 10L);
    }

    private record RegionEntry(int slot, Material material, String name,
                               List<String> baseLore, String regionKey) {}

    // ── helpers ──────────────────────────────────────────────────────

    private ItemStack buildItem(Material material, String name, List<String> loreRaw) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(PlatformText.legacy(plugin.messageService().deserialize(name)));
            if (!loreRaw.isEmpty()) {
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                for (String line : loreRaw) {
                    lore.add(plugin.messageService().deserialize(line));
                }
                meta.setLore(lore.stream().map(PlatformText::legacy).toList());
            }
            // Keep tooltips clean: only the name and lore should show.
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

}
