package com.telehop.paper.gui;

import com.telehop.common.PermissionNodes;
import com.telehop.common.model.HomeRecord;
import com.telehop.common.service.HomeService;
import com.telehop.paper.NetworkPaperPlugin;
import com.telehop.paper.config.PaperSettings;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
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
 * Up to 10 beds arranged across two rows (row 2 and row 4).
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
            Bukkit.getScheduler().runTask(plugin, () -> {
                int rows = Math.max(1, Math.min(6, cfg.homeGuiRows()));
                Gui gui = Gui.gui()
                        .title(plugin.messageService().deserialize(cfg.homeGuiTitle()))
                        .rows(rows)
                        .create();
                gui.setDefaultClickAction(event -> event.setCancelled(true));

                int totalDisplaySlots = cfg.homeMaxSlots();
                int slotsPerRow = Math.min(5, totalDisplaySlots);
                int secondRowCount = Math.max(0, totalDisplaySlots - slotsPerRow);

                int row1Start = 1 * 9 + centerOffset(slotsPerRow);
                int row2Start = 3 * 9 + centerOffset(secondRowCount);

                HomeRecord[] slotMap = mapHomesToSlots(homes, totalDisplaySlots);

                for (int i = 0; i < totalDisplaySlots; i++) {
                    boolean isSecondRow = i >= slotsPerRow;
                    int guiIndex = isSecondRow
                            ? row2Start + (i - slotsPerRow)
                            : row1Start + i;

                    boolean hasPermission = (i + 1) <= maxSlots;
                    HomeRecord existing = slotMap[i];

                    if (existing != null) {
                        List<String> lore = new ArrayList<>();
                        lore.add("<gray>Server: " + prettyServer(existing.server(), cfg));
                        lore.add("<gray>World: " + prettyWorld(existing.world(), cfg));
                        if (cfg.homeShowLocation()) {
                            lore.add("<gray>Location: <white>" + (int) existing.x() + ", " + (int) existing.y() + ", " + (int) existing.z());
                        }
                        lore.add("");
                        lore.add("<yellow>Click to manage");
                        ItemStack item = buildItem(cfg.homeSetBed(), "<green>" + existing.name(), lore);
                        final HomeRecord home = existing;
                        gui.setItem(guiIndex, ItemBuilder.from(item).asGuiItem(click -> openManageMenu(player, home)));
                    } else if (hasPermission) {
                        boolean blocked = cfg.isHomeBlockedOnCurrentServer();
                        String autoName = "Home " + (i + 1);
                        ItemStack item = buildItem(cfg.homeEmptyBed(),
                                "<gold>Slot " + (i + 1) + " <gray>(Empty)",
                                blocked ? List.of("<red>Cannot set homes on this server")
                                        : List.of("<yellow>Click to set home here"));
                        gui.setItem(guiIndex, ItemBuilder.from(item).asGuiItem(click -> {
                            if (blocked) return;
                            Location loc = player.getLocation();
                            if (loc.getWorld() == null) return;
                            HomeRecord newHome = new HomeRecord(uuid, autoName,
                                    cfg.serverName(), loc.getWorld().getName(),
                                    loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
                            player.closeInventory();
                            homeService.upsert(newHome).thenRun(() ->
                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                        player.sendMessage(plugin.msg("home-set", Map.of("name", autoName)));
                                        open(player);
                                    }));
                        }));
                    } else {
                        ItemStack item = buildItem(cfg.homeLockedBed(),
                                "<blue>Slot " + (i + 1) + " <gray>(Locked)",
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
                .title(plugin.messageService().deserialize("<gold>" + home.name()))
                .rows(3)
                .create();
        gui.setDefaultClickAction(event -> event.setCancelled(true));

        gui.setItem(12, ItemBuilder.from(buildItem(Material.LIME_WOOL, "<green>Teleport",
                List.of("<gray>Go to " + home.name()))).asGuiItem(click -> {
            player.closeInventory();
            teleportToHome(player, home);
        }));

        gui.setItem(14, ItemBuilder.from(buildItem(Material.RED_WOOL, "<red>Delete",
                List.of("<gray>Remove " + home.name()))).asGuiItem(click -> openConfirmDelete(player, home)));

        gui.setItem(18, ItemBuilder.from(buildItem(Material.SPECTRAL_ARROW, "<gold>Back",
                List.of("<gray>Return to homes"))).asGuiItem(click -> open(player)));

        gui.open(player);
    }

    private void openConfirmDelete(Player player, HomeRecord home) {
        Gui gui = Gui.gui()
                .title(plugin.messageService().deserialize("<red>Delete " + home.name() + "?"))
                .rows(3)
                .create();
        gui.setDefaultClickAction(event -> event.setCancelled(true));

        gui.setItem(12, ItemBuilder.from(buildItem(Material.LIME_WOOL, "<green>Confirm", List.of()))
                .asGuiItem(click -> {
                    player.closeInventory();
                    homeService.delete(home.uuid(), home.name()).thenRun(() ->
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                player.sendMessage(plugin.msg("home-deleted", Map.of("name", home.name())));
                                open(player);
                            }));
                }));

        gui.setItem(14, ItemBuilder.from(buildItem(Material.RED_WOOL, "<red>Cancel", List.of()))
                .asGuiItem(click -> openManageMenu(player, home)));

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
            plugin.messaging().send(packet);
            player.sendMessage(plugin.msg("home-teleporting", Map.of("name", home.name())));
            return;
        }
        org.bukkit.World world = Bukkit.getWorld(home.world());
        if (world == null) {
            player.sendMessage(plugin.msg("home-not-found"));
            return;
        }
        Location target = new Location(world, home.x(), home.y(), home.z(), home.yaw(), home.pitch());
        player.sendMessage(plugin.msg("home-teleporting", Map.of("name", home.name())));
        plugin.services().teleportService().teleportToHome(player, target);
    }

    private int resolveMaxSlots(Player player) {
        for (int i = plugin.settings().homeMaxSlots(); i >= 1; i--) {
            if (player.hasPermission(PermissionNodes.HOME_LIMIT_PREFIX + i)) {
                return i;
            }
        }
        return 1;
    }

    private static final java.util.regex.Pattern HOME_NUM = java.util.regex.Pattern.compile("^Home (\\d+)$", java.util.regex.Pattern.CASE_INSENSITIVE);

    private static HomeRecord[] mapHomesToSlots(List<HomeRecord> homes, int totalSlots) {
        HomeRecord[] slots = new HomeRecord[totalSlots];
        List<HomeRecord> unslotted = new ArrayList<>();

        for (HomeRecord home : homes) {
            java.util.regex.Matcher m = HOME_NUM.matcher(home.name());
            if (m.matches()) {
                int num = Integer.parseInt(m.group(1));
                if (num >= 1 && num <= totalSlots && slots[num - 1] == null) {
                    slots[num - 1] = home;
                    continue;
                }
            }
            unslotted.add(home);
        }

        int nextFree = 0;
        for (HomeRecord home : unslotted) {
            while (nextFree < totalSlots && slots[nextFree] != null) nextFree++;
            if (nextFree < totalSlots) {
                slots[nextFree] = home;
                nextFree++;
            }
        }
        return slots;
    }

    private static int centerOffset(int itemCount) {
        if (itemCount <= 0) return 0;
        return Math.max(0, (9 - itemCount) / 2);
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
