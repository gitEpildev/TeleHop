package com.telehop.paper;

import co.aikar.commands.PaperCommandManager;
import com.telehop.common.db.*;
import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import com.telehop.common.model.PlayerWarpRecord;
import com.telehop.common.service.*;
import com.telehop.paper.command.*;
import com.telehop.paper.command.admin.*;
import com.telehop.paper.command.tpa.*;
import com.telehop.paper.command.warp.*;
import com.telehop.paper.config.PaperSettings;
import com.telehop.paper.config.StorageManager;
import com.telehop.paper.handler.PacketHandler;
import com.telehop.paper.listener.PaperPlayerListener;
import com.telehop.paper.messaging.PaperMessagingManager;
import com.telehop.paper.service.*;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;

/**
 * Performs the full plugin startup sequence and constructs the {@link ServiceRegistry}.
 * Keeps {@link NetworkPaperPlugin} thin — lifecycle only.
 */
public final class Bootstrap {
    private static final List<String> LANGUAGE_CODES = List.of("en", "nl", "de", "es", "zh", "pl");
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private Bootstrap() {}

    /**
     * Initialises every service, registers commands, listeners, and scheduled tasks.
     *
     * @return a fully wired {@link ServiceRegistry}, or {@code null} if startup fails
     */
    public static ServiceRegistry init(NetworkPaperPlugin plugin) {
        plugin.saveDefaultConfig();
        ServiceRegistry reg = new ServiceRegistry();

        PaperSettings settings = PaperSettings.from(plugin.getConfig());
        reg.setSettings(settings);

        initLanguageFiles(plugin);
        reg.setMessageService(buildMessageService(plugin, settings));
        reg.setPermissionService(new PermissionService());
        reg.setAuditLogger(new AuditLogger(plugin, settings.auditEnabled()));
        reg.setPendingTeleportManager(new PendingTeleportManager());
        reg.setRtpManager(new RtpManager(plugin));
        reg.setTpaRuntimeManager(new TpaRuntimeManager());
        reg.setNetworkPlayerNameCache(new NetworkPlayerNameCache());

        StorageManager storage = new StorageManager(plugin);
        storage.load();
        reg.setStorageManager(storage);
        reg.setTeleportService(new TeleportService(plugin, reg.pendingTeleportManager(), storage));

        DatabaseManager db;
        try {
            db = new DatabaseManager(settings.databaseConfig());
            db.initSchema();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            Bukkit.getPluginManager().disablePlugin(plugin);
            return null;
        }
        reg.setDatabaseManager(db);

        reg.setPlayerService(new PlayerService(db, new PlayerRepository(db.dataSource()), new PlayerServerCache()));
        reg.setWarpService(new WarpService(db, new WarpRepository(db.dataSource()), new WarpCache()));
        reg.setPlayerWarpService(new PlayerWarpService(db, new PlayerWarpRepository(db.dataSource())));
        reg.setTpaService(new TpaService(db, new TpaRepository(db.dataSource()), new TpaRequestCache()));
        reg.warpService().refreshCache();

        reg.teleportService().wire(reg.messageService(), reg.auditLogger(), reg.rtpManager());

        PaperMessagingManager messaging = new PaperMessagingManager(plugin, settings.dedupeWindowMs(), settings.requestTimeoutMs());
        messaging.register();
        messaging.setHandler(new PacketHandler(plugin, reg));
        reg.setMessaging(messaging);

        plugin.getServer().getPluginManager().registerEvents(new PaperPlayerListener(plugin), plugin);
        registerCommands(plugin, reg);
        startScheduledTasks(plugin, reg);

        return reg;
    }

    public static void shutdown(ServiceRegistry reg) {
        if (reg == null) return;
        if (reg.messaging() != null) reg.messaging().shutdown();
        if (reg.databaseManager() != null) reg.databaseManager().shutdown();
    }

    public static void reload(NetworkPaperPlugin plugin, ServiceRegistry reg) {
        plugin.reloadConfig();
        PaperSettings settings = PaperSettings.from(plugin.getConfig());
        reg.setSettings(settings);
        initLanguageFiles(plugin);
        reg.setMessageService(buildMessageService(plugin, settings));
        reg.warpService().refreshCache();
        reg.setAuditLogger(new AuditLogger(plugin, settings.auditEnabled()));
        plugin.getLogger().info("TeleHop configuration reloaded (language: " + settings.language() + ").");
    }

    // ── internal helpers ─────────────────────────────────────────────

    static void initLanguageFiles(NetworkPaperPlugin plugin) {
        File langDir = new File(plugin.getDataFolder(), "languages");
        langDir.mkdirs();
        for (String code : LANGUAGE_CODES) {
            File target = new File(langDir, code + ".yml");
            if (!target.exists()) {
                plugin.saveResource("languages/" + code + ".yml", false);
            }
        }
    }

    static MessageService buildMessageService(NetworkPaperPlugin plugin, PaperSettings settings) {
        String lang = settings.language();
        File langDir = new File(plugin.getDataFolder(), "languages");
        File selected = new File(langDir, lang + ".yml");
        if (!selected.exists()) {
            plugin.getLogger().warning("Language '" + lang + "' not found, falling back to English.");
            selected = new File(langDir, "en.yml");
        }
        FileConfiguration langFile = YamlConfiguration.loadConfiguration(selected);
        FileConfiguration fallback = YamlConfiguration.loadConfiguration(new File(langDir, "en.yml"));
        return new MessageService(langFile, fallback);
    }

    private static void registerCommands(NetworkPaperPlugin plugin, ServiceRegistry reg) {
        PaperCommandManager manager = new PaperCommandManager(plugin);
        manager.enableUnstableAPI("brigadier");
        manager.enableUnstableAPI("help");

        try {
            String consoleMini = reg.messageService().rawString("console-not-allowed");
            net.kyori.adventure.text.Component comp = MINI.deserialize(consoleMini);
            String legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().serialize(comp);
            manager.getLocales().addMessage(java.util.Locale.ENGLISH,
                    co.aikar.commands.MessageKeys.NOT_ALLOWED_ON_CONSOLE, legacy);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not set ACF console message: " + e.getMessage());
        }

        manager.getCommandCompletions().registerCompletion("networkplayers",
                c -> reg.networkPlayerNameCache().list());
        manager.getCommandCompletions().registerCompletion("warps",
                c -> reg.warpService().listCached().stream()
                        .map(com.telehop.common.model.WarpRecord::name).toList());
        manager.getCommandCompletions().registerCompletion("playerwarps", c -> {
            if (c.getPlayer() != null) {
                return reg.playerWarpService()
                        .listByOwner(c.getPlayer().getUniqueId().toString())
                        .join().stream().map(PlayerWarpRecord::name).toList();
            }
            return List.of();
        });

        manager.registerCommand(new SpawnCommand(plugin));
        manager.registerCommand(new WarpCommand(plugin));
        manager.registerCommand(new SetWarpCommand(plugin));
        manager.registerCommand(new DelWarpCommand(plugin));
        manager.registerCommand(new WarpsCommand(plugin));
        manager.registerCommand(new TpaCommand(plugin));
        manager.registerCommand(new TpaHereCommand(plugin));
        manager.registerCommand(new TpaAcceptCommand(plugin));
        manager.registerCommand(new TpaDenyCommand(plugin));
        manager.registerCommand(new TpaCancelCommand(plugin));
        manager.registerCommand(new AdminTeleportCommand(plugin));
        manager.registerCommand(new TpHereAdminCommand(plugin));
        manager.registerCommand(new RtpCommand(plugin));
        manager.registerCommand(new PlayerWarpCommand(plugin));
        manager.registerCommand(new TeleHopCommand(plugin));
        manager.registerCommand(new ListWarpsCommand(plugin));
        manager.registerCommand(new ForceDelWarpCommand(plugin));
    }

    private static void startScheduledTasks(NetworkPaperPlugin plugin, ServiceRegistry reg) {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () ->
                reg.tpaService().expiredNow(reg.settings().tpaTimeoutSeconds()).thenAccept(expired -> {
                    for (var request : expired) {
                        reg.tpaService().delete(request.senderUuid(), request.targetUuid());
                        reg.tpaRuntimeManager().removeIncoming(request.targetUuid());
                    }
                    if (!expired.isEmpty()) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            for (var request : expired) {
                                Player sender = Bukkit.getPlayer(request.senderUuid());
                                if (sender != null) sender.sendMessage(plugin.msg("request-expired"));
                                Player target = Bukkit.getPlayer(request.targetUuid());
                                if (target != null) target.sendMessage(plugin.msg("request-expired"));
                            }
                        });
                    }
                }), 20L, 20L * 5L);

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            NetworkPacket packet = NetworkPacket.request(PacketType.PLAYER_LIST_REQUEST,
                    reg.settings().serverName(), "velocity");
            reg.messaging().send(packet);
        }, 40L, 20L * 5L);
    }
}
