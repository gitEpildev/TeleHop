package com.telehop.paper.gui;

import com.telehop.paper.platform.PlatformText;
import com.telehop.paper.service.HomeAllowance;
import com.telehop.paper.service.HomeCreation;
import com.telehop.common.model.HomeRecord;
import com.telehop.common.service.HomeService;
import com.telehop.paper.NetworkPaperPlugin;
import com.telehop.paper.config.PaperSettings;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Chest GUI (5 rows / 45 slots) for managing named homes.
 * Each page has ten beds and uses the lower corners for navigation.
 */
public class HomeGui {
    private final NetworkPaperPlugin plugin;
    private final HomeService homeService;

    public HomeGui(NetworkPaperPlugin plugin, HomeService homeService) {
        this.plugin = plugin;
        this.homeService = homeService;
    }

    public void open(Player player) {
        open(player, 0);
    }

    private void open(Player player, int requested) {
        int allowance = HomeAllowance.forPlayer(player, plugin.settings().homeCreationCap());
        homeService.listByPlayer(player.getUniqueId().toString())
                .thenApply(homes -> HomePage.of(homes, requested, allowance))
                .thenAccept(page -> plugin.onMainThread(() -> {
                    if (player.isOnline()) render(player, page);
                }));
    }

    private void render(Player player, HomePage page) {
        PaperSettings cfg = plugin.settings();
        if (page.allowance() != HomeAllowance.forPlayer(player, cfg.homeCreationCap())) {
            open(player, page.index());
            return;
        }
        int rows = Math.clamp(cfg.homeGuiRows(), 2, 6);
        var messages = plugin.messageService();
        var pageLabel = messages.rawFormat("home-page-title", Map.of("page", String.valueOf(page.index() + 1)));
        Gui gui = Gui.gui()
                .title(HomePageTitle.format(messages.deserialize(cfg.homeGuiTitle()), pageLabel))
                .rows(rows)
                .disableAllInteractions()
                .create();

        for (int i = 0; i < HomePage.SIZE; i++) {
            int guiIndex = HomePage.bedSlot(i, rows);
            long number = page.number(i);
            HomeRecord existing = page.bed(i);

            if (existing != null) {
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("Server: ", NamedTextColor.GRAY).append(prettyServer(existing.server(), cfg)));
                lore.add(Component.text("World: ", NamedTextColor.GRAY)
                        .append(plugin.messageService().deserialize(prettyWorld(existing.world(), cfg))));
                if (cfg.homeShowLocation()) {
                    lore.add(Component.text("Location: ", NamedTextColor.GRAY).append(Component.text(
                            (int) existing.x() + ", " + (int) existing.y() + ", " + (int) existing.z(), NamedTextColor.WHITE)));
                }
                lore.add(Component.empty());
                lore.add(Component.text("Click to manage", NamedTextColor.YELLOW));
                ItemStack item = buildItem(cfg.homeSetBed(), Component.text(existing.name(), NamedTextColor.GREEN), lore);
                final HomeRecord home = existing;
                gui.setItem(guiIndex, ItemBuilder.from(item).asGuiItem(click -> openManageMenu(player, home, page.index())));
            } else if (page.canCreate(i)) {
                boolean blocked = cfg.isHomeBlockedOnCurrentServer();
                String autoName = "Home " + number;
                ItemStack item = buildItem(cfg.homeEmptyBed(),
                        "<gold>Slot " + number + " <gray>(Empty)",
                        blocked ? List.of("<red>Cannot set homes on this server")
                                : List.of("<yellow>Click to set home here"));
                gui.setItem(guiIndex, ItemBuilder.from(item).asGuiItem(click -> {
                    if (blocked) return;
                    player.closeInventory();
                    HomeCreation.save(plugin, player, autoName, false, () -> open(player, page.index()));
                }));
            } else {
                ItemStack item = buildItem(cfg.homeLockedBed(),
                        "<blue>Slot " + number + " <gray>(Locked)",
                        List.of("<red>Upgrade to unlock this slot"));
                gui.setItem(guiIndex, ItemBuilder.from(item).asGuiItem(click -> {}));
            }
        }
        int footer = (rows - 1) * 9;
        if (page.index() < page.last()) {
            gui.setItem(footer, ItemBuilder.from(buildItem(Material.SPECTRAL_ARROW, plugin.messageService().raw("home-page-next"), List.of()))
                    .asGuiItem(click -> render(player, page.turn(page.index() + 1))));
        }
        gui.setItem(footer + 8, ItemBuilder.from(buildItem(Material.BARRIER,
                plugin.messageService().raw(page.index() == 0 ? "home-page-exit" : "home-page-back"), List.of()))
                .asGuiItem(click -> {
                    if (page.index() == 0) player.closeInventory();
                    else render(player, page.turn(page.index() - 1));
                }));
        gui.open(player);
    }

    private void openManageMenu(Player player, HomeRecord home, int page) {
        Gui gui = Gui.gui()
                .title(Component.text(home.name(), NamedTextColor.GOLD))
                .rows(3)
                .disableAllInteractions()
                .create();

        gui.setItem(12, ItemBuilder.from(buildItem(Material.LIME_WOOL, Component.text("Teleport", NamedTextColor.GREEN),
                List.of(Component.text("Go to " + home.name(), NamedTextColor.GRAY)))).asGuiItem(click -> {
            player.closeInventory();
            teleportToHome(player, home);
        }));

        gui.setItem(14, ItemBuilder.from(buildItem(Material.RED_WOOL, Component.text("Delete", NamedTextColor.RED),
                List.of(Component.text("Remove " + home.name(), NamedTextColor.GRAY)))).asGuiItem(click -> openConfirmDelete(player, home, page)));

        gui.setItem(26, ItemBuilder.from(buildItem(Material.BARRIER, plugin.messageService().raw("home-page-back"), List.of()))
                .asGuiItem(click -> open(player, page)));

        gui.open(player);
    }

    private void openConfirmDelete(Player player, HomeRecord home, int page) {
        Gui gui = Gui.gui()
                .title(Component.text("Delete " + home.name() + "?", NamedTextColor.RED))
                .rows(3)
                .disableAllInteractions()
                .create();

        gui.setItem(12, ItemBuilder.from(buildItem(Material.LIME_WOOL, "<green>Confirm", List.of()))
                .asGuiItem(click -> {
                    player.closeInventory();
                    homeService.delete(home.uuid(), home.name()).thenRun(() ->
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                PlatformText.send(player, plugin.msg("home-deleted", Map.of("name", home.name())));
                                open(player, page);
                            }));
                }));

        gui.setItem(14, ItemBuilder.from(buildItem(Material.RED_WOOL, "<red>Cancel", List.of()))
                .asGuiItem(click -> openManageMenu(player, home, page)));

        gui.open(player);
    }

    public void teleportToHome(Player player, HomeRecord home) {
        String currentServer = plugin.settings().serverName();
        if (!home.server().equalsIgnoreCase(currentServer)) {
            com.telehop.common.model.NetworkPacket packet = com.telehop.common.model.NetworkPacket.request(
                    com.telehop.common.model.PacketType.TRANSFER_PLAYER,
                    currentServer, "velocity");
            packet.put("uuid", player.getUniqueId().toString());
            packet.put("targetServer", home.server());
            packet.put("postAction", "HOME");
            packet.put("homeName", home.name());
            packet.put("homeUuid", home.uuid());
            plugin.messaging().send(player, packet);
            PlatformText.send(player, plugin.msg("home-teleporting", Map.of("name", home.name())));
            return;
        }
        org.bukkit.World world = plugin.versionAdapter().resolveWorld(home.world());
        if (world == null) {
            PlatformText.send(player, plugin.msg("home-not-found"));
            return;
        }
        Location target = new Location(world, home.x(), home.y(), home.z(), home.yaw(), home.pitch());
        PlatformText.send(player, plugin.msg("home-teleporting", Map.of("name", home.name())));
        plugin.services().teleportService().teleportToHome(player, target);
    }

    private static String prettyWorld(String worldName, PaperSettings cfg) {
        if (worldName == null) return "<white>Unknown";
        String lower = worldName.toLowerCase();
        if (lower.contains("nether")) return cfg.homeWorldNether();
        if (lower.contains("end")) return cfg.homeWorldEnd();
        return cfg.homeWorldOverworld();
    }

    private Component prettyServer(String serverName, PaperSettings cfg) {
        if (serverName == null) return Component.text("Unknown", NamedTextColor.WHITE);
        String color = cfg.homeServerColors().get(serverName.toLowerCase());
        return color != null ? plugin.messageService().deserialize(color) : Component.text(serverName, NamedTextColor.WHITE);
    }

    private ItemStack buildItem(Material material, String name, List<String> loreRaw) {
        return buildItem(material, plugin.messageService().deserialize(name),
                loreRaw.stream().map(plugin.messageService()::deserialize).toList());
    }

    private ItemStack buildItem(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(PlatformText.legacy(name));
            if (!lore.isEmpty()) meta.setLore(lore.stream().map(PlatformText::legacy).toList());
            item.setItemMeta(meta);
        }
        return item;
    }
}
