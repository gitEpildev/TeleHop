package com.telehop.paper.gui;

import com.telehop.common.model.HomeRecord;
import com.telehop.common.service.HomeService;
import com.telehop.paper.NetworkPaperPlugin;
import com.telehop.paper.config.PaperSettings;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Single-chest GUI (3 rows / 27 slots) for managing homes.
 * Beds are centered in the middle row. Colours are configurable in home.yml:
 *   bed-set    (default LIME_BED)       — occupied home
 *   bed-empty  (default RED_BED)        — available slot, click to set
 *   bed-locked (default LIGHT_BLUE_BED) — no permission
 */
public class HomeGui {
    private final NetworkPaperPlugin plugin;
    private final HomeService homeService;

    public HomeGui(NetworkPaperPlugin plugin, HomeService homeService) {
        this.plugin = plugin;
        this.homeService = homeService;
    }

    public void open(Player player) {
        String uuid = player.getUniqueId().toString();
        int maxSlots = resolveMaxSlots(player);
        PaperSettings cfg = plugin.settings();

        homeService.listByPlayer(uuid).thenAccept(homes -> {
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                int rows = Math.max(1, Math.min(6, cfg.homeGuiRows()));
                Gui gui = Gui.gui()
                        .title(plugin.messageService().deserialize(cfg.homeGuiTitle()))
                        .rows(rows)
                        .disableAllInteractions()
                        .create();

                int totalSlots = cfg.homeMaxSlots();
                int middleRow = rows / 2;
                int startCol = Math.max(0, (9 - totalSlots) / 2);
                int baseSlot = middleRow * 9 + startCol;

                for (int slot = 1; slot <= totalSlots; slot++) {
                    final int homeSlot = slot;
                    boolean hasPermission = slot <= maxSlots;
                    HomeRecord existing = findBySlot(homes, slot);
                    int guiIndex = baseSlot + slot - 1;

                    if (existing != null) {
                        List<String> lore = new ArrayList<>();
                        lore.add("<gray>Server: " + prettyServer(existing.server(), cfg));
                        lore.add("<gray>World: " + prettyWorld(existing.world(), cfg));
                        if (cfg.homeShowLocation()) {
                            lore.add("<gray>Location: <white>" + (int) existing.x() + ", " + (int) existing.y() + ", " + (int) existing.z());
                        }
                        lore.add("");
                        lore.add("<yellow>Click to manage");
                        ItemStack item = buildItem(cfg.homeSetBed(),
                                "<green>Home " + slot,
                                lore);
                        gui.setItem(guiIndex, ItemBuilder.from(item).asGuiItem(click ->
                                openManageMenu(player, existing)));
                    } else if (hasPermission) {
                        boolean blocked = cfg.isHomeBlockedOnCurrentServer();
                        ItemStack item = buildItem(cfg.homeEmptyBed(),
                                "<gold>Home " + slot + " <gray>(Empty)",
                                blocked ? List.of("<red>Cannot set homes on this server")
                                        : List.of("<yellow>Click to set home here"));
                        gui.setItem(guiIndex, ItemBuilder.from(item).asGuiItem(click -> {
                            if (blocked) {
                                player.sendMessage(plugin.msg("home-blocked-server"));
                                return;
                            }
                            if (cfg.homeConfirmSet()) {
                                openConfirmSet(player, homeSlot);
                            } else {
                                setHome(player, homeSlot);
                            }
                        }));
                    } else {
                        ItemStack item = buildItem(cfg.homeLockedBed(),
                                "<blue>Home " + slot + " <gray>(Locked)",
                                List.of("<red>Upgrade to unlock this slot"));
                        gui.setItem(guiIndex, ItemBuilder.from(item).asGuiItem(click -> {}));
                    }
                }
                gui.open(player);
            });
        });
    }

    private void openManageMenu(Player player, HomeRecord home) {
        Gui gui = Gui.gui()
                .title(plugin.messageService().deserialize("<gold>Home " + home.slot()))
                .rows(3)
                .disableAllInteractions()
                .create();

        ItemStack tpItem = buildItem(Material.LIME_WOOL, "<green>Teleport",
                List.of("<gray>Go to Home " + home.slot()));
        gui.setItem(12, ItemBuilder.from(tpItem).asGuiItem(click -> {
            player.closeInventory();
            teleportToHome(player, home);
        }));

        ItemStack delItem = buildItem(Material.RED_WOOL, "<red>Delete",
                List.of("<gray>Remove Home " + home.slot()));
        gui.setItem(14, ItemBuilder.from(delItem).asGuiItem(click ->
                openConfirmDelete(player, home)));

        gui.open(player);
    }

    private void openConfirmSet(Player player, int slot) {
        Gui gui = Gui.gui()
                .title(plugin.messageService().deserialize("<yellow>Set Home " + slot + "?"))
                .rows(3)
                .disableAllInteractions()
                .create();

        gui.setItem(12, ItemBuilder.from(buildItem(Material.LIME_WOOL, "<green>Confirm", List.of()))
                .asGuiItem(click -> {
                    player.closeInventory();
                    setHome(player, slot);
                }));
        gui.setItem(14, ItemBuilder.from(buildItem(Material.RED_WOOL, "<red>Cancel", List.of()))
                .asGuiItem(click -> {
                    player.closeInventory();
                    open(player);
                }));
        gui.open(player);
    }

    private void openConfirmDelete(Player player, HomeRecord home) {
        Gui gui = Gui.gui()
                .title(plugin.messageService().deserialize("<red>Delete Home " + home.slot() + "?"))
                .rows(3)
                .disableAllInteractions()
                .create();

        gui.setItem(12, ItemBuilder.from(buildItem(Material.LIME_WOOL, "<green>Confirm", List.of()))
                .asGuiItem(click -> {
                    player.closeInventory();
                    homeService.delete(home.uuid(), home.slot()).thenRun(() ->
                            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                                player.sendMessage(plugin.msg("home-deleted", Map.of("slot", String.valueOf(home.slot()))));
                                open(player);
                            }));
                }));
        gui.setItem(14, ItemBuilder.from(buildItem(Material.RED_WOOL, "<red>Cancel", List.of()))
                .asGuiItem(click -> {
                    player.closeInventory();
                    openManageMenu(player, home);
                }));
        gui.open(player);
    }

    private void setHome(Player player, int slot) {
        Location loc = player.getLocation();
        HomeRecord home = new HomeRecord(
                player.getUniqueId().toString(), slot,
                plugin.settings().serverName(), loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        homeService.upsert(home).thenRun(() ->
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(plugin.msg("home-set", Map.of("slot", String.valueOf(slot))));
                    open(player);
                }));
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
            packet.put("homeSlot", String.valueOf(home.slot()));
            packet.put("homeUuid", home.uuid());
            plugin.messaging().send(packet);
            player.sendMessage(plugin.msg("home-teleporting", Map.of("slot", String.valueOf(home.slot()))));
            return;
        }
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(home.world());
        if (world == null) {
            player.sendMessage(plugin.msg("home-not-found"));
            return;
        }
        Location target = new Location(world, home.x(), home.y(), home.z(), home.yaw(), home.pitch());
        player.sendMessage(plugin.msg("home-teleporting", Map.of("slot", String.valueOf(home.slot()))));
        plugin.services().teleportService().teleportToHome(player, target);
    }

    private int resolveMaxSlots(Player player) {
        int max = 0;
        for (int i = plugin.settings().homeMaxSlots(); i >= 1; i--) {
            if (player.hasPermission("telehop.homes." + i)) {
                max = i;
                break;
            }
        }
        return max;
    }

    private static String prettyWorld(String worldName, PaperSettings cfg) {
        if (worldName == null) return "<white>Unknown";
        String lower = worldName.toLowerCase();
        if (lower.contains("nether")) return cfg.homeWorldNether();
        if (lower.contains("end")) return cfg.homeWorldEnd();
        return cfg.homeWorldOverworld();
    }

    private static String prettyServer(String serverName, PaperSettings cfg) {
        if (serverName == null) return "<white>Unknown";
        String color = cfg.homeServerColors().get(serverName.toLowerCase());
        return color != null ? color : "<white>" + serverName;
    }

    private HomeRecord findBySlot(List<HomeRecord> homes, int slot) {
        for (HomeRecord h : homes) {
            if (h.slot() == slot) return h;
        }
        return null;
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
}
