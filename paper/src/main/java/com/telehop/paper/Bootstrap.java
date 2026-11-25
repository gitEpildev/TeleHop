package com.telehop.paper;

import co.aikar.commands.PaperCommandManager;
import com.telehop.common.db.DatabaseManager;
import com.telehop.common.db.HomeRepository;
import com.telehop.common.db.LastLocationRepository;
import com.telehop.common.db.PlayerRepository;
import com.telehop.common.db.PlayerWarpRepository;
import com.telehop.common.db.TpaRepository;
import com.telehop.common.db.WarpRepository;
import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import com.telehop.common.model.PlayerWarpRecord;
import com.telehop.common.service.HomeService;
import com.telehop.common.service.LastLocationService;
import com.telehop.common.service.PlayerServerCache;
import com.telehop.common.service.PlayerService;
import com.telehop.common.service.PlayerWarpService;
import com.telehop.common.service.TpaRequestCache;
import com.telehop.common.service.TpaService;
import com.telehop.common.service.WarpCache;
import com.telehop.common.service.WarpService;
import com.telehop.paper.command.BackCommand;
import com.telehop.paper.command.RtpCommand;
import com.telehop.paper.command.SpawnCommand;
import com.telehop.paper.command.HubCommand;
import com.telehop.paper.config.SpawnRoutes;
import com.telehop.paper.service.SpawnService;
import com.telehop.common.service.SpawnDirectory;
import com.telehop.common.db.SpawnRepository;
import com.telehop.paper.command.admin.AdminTeleportCommand;
import com.telehop.paper.command.admin.ForceDelHomeCommand;
import com.telehop.paper.command.admin.ForceLastLocCommand;
import com.telehop.paper.command.admin.ForceSetHomeCommand;
import com.telehop.paper.command.admin.ListHomesCommand;
import com.telehop.paper.command.admin.PlayerInfoCommand;
import com.telehop.paper.command.admin.TeleHopCommand;
import com.telehop.paper.command.admin.TpHereAdminCommand;
import com.telehop.paper.command.home.DelHomeCommand;
import com.telehop.paper.command.home.HomeCommand;
import com.telehop.paper.command.home.SetHomeCommand;
import com.telehop.paper.command.LastLocationCommand;
import com.telehop.paper.command.tpa.TpaAcceptCommand;
import com.telehop.paper.command.tpa.TpaCancelCommand;
import com.telehop.paper.command.tpa.TpaCommand;
import com.telehop.paper.command.tpa.TpaDenyCommand;
import com.telehop.paper.command.tpa.TpaHereCommand;
import com.telehop.paper.command.tpa.TpaToggleCommand;
import com.telehop.paper.command.warp.DelWarpCommand;
import com.telehop.paper.command.warp.ForceDelWarpCommand;
import com.telehop.paper.command.warp.ListWarpsCommand;
import com.telehop.paper.command.warp.PlayerWarpCommand;
import com.telehop.paper.command.warp.SetWarpCommand;
import com.telehop.paper.command.warp.WarpCommand;
import com.telehop.paper.command.warp.WarpsCommand;
import com.telehop.paper.config.ConfigMigrator;
import com.telehop.paper.config.CommandNames;
import com.telehop.paper.config.PaperSettings;
import com.telehop.paper.config.StorageManager;
import com.telehop.paper.handler.PacketHandler;
import com.telehop.paper.listener.PaperPlayerListener;
import com.telehop.paper.listener.RespawnListener;
import com.telehop.paper.messaging.PaperMessagingManager;
import com.telehop.paper.service.AuditLogger;
import com.telehop.paper.service.BackLocationManager;
import com.telehop.paper.service.MessageService;
import com.telehop.paper.service.NetworkPlayerNameCache;
import com.telehop.paper.service.PendingTeleportManager;
import com.telehop.paper.service.PermissionService;
import com.telehop.paper.service.PingService;
import com.telehop.paper.service.RandomRespawnManager;
import com.telehop.paper.service.RandomRespawnService;
import com.telehop.paper.service.RtpManager;
import com.telehop.paper.service.ServiceRegistry;
import com.telehop.paper.service.TeleportEffectPlayer;
import com.telehop.paper.service.TeleportService;
import com.telehop.paper.service.TpaRuntimeManager;
import com.telehop.paper.version.VersionAdapter;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Performs the full plugin startup sequence and constructs the {@link ServiceRegistry}.
 * Keeps {@link NetworkPaperPlugin} thin, lifecycle only.
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
    private static final String[] CONFIG_TEMPLATES = {
            "config/general.yml", "config/database.yml",
            "config/teleport.yml", "config/tpa.yml", "config/rtp.yml",
            "config/spawns.yml", "config/home.yml", "config/respawn.yml", "config/WIKI.md"
    };

    public static ServiceRegistry init(NetworkPaperPlugin plugin) {
        printBanner(plugin);

        VersionAdapter versionAdapter = resolveVersionAdapter(plugin);
        if (versionAdapter == null) {
            plugin.getLogger().severe("Unsupported Minecraft version! TeleHop supports Paper 1.21.x and 26.1.x.");
            Bukkit.getPluginManager().disablePlugin(plugin);
            return null;
        }
        plugin.setVersionAdapter(versionAdapter);

        if (ConfigMigrator.needsMigration(plugin)) {
            try {
                ConfigMigrator.migrate(plugin);
            } catch (IOException | InvalidConfigurationException ex) {
                plugin.getLogger().log(Level.SEVERE, "Could not migrate the legacy Paper configuration", ex);
                Bukkit.getPluginManager().disablePlugin(plugin);
                return null;
            }
        }
        initConfigFiles(plugin);
        SpawnRoutes routes = SpawnRoutes.load(plugin.getDataFolder());
        try {
            ConfigMigrator.completeCommandSettings(plugin, routes.legacyHubCommand());
            ConfigMigrator.refreshBundledGuide(plugin);
        } catch (IOException | InvalidConfigurationException ex) {
            throw new IllegalStateException("Cannot upgrade command or feature configuration", ex);
        }
        PaperSettings settings = PaperSettings.load(plugin);
        boolean hubCommand = settings.isFeatureEnabled("hub");
        plugin.setCommandNames(CommandNames.load(plugin, hubCommand));
        ServiceRegistry reg = new ServiceRegistry();
        reg.setSettings(settings);

        initLanguageFiles(plugin);
        reg.setMessageService(buildMessageService(plugin, settings));
        reg.setPermissionService(new PermissionService());
        reg.setAuditLogger(new AuditLogger(plugin, settings.auditEnabled()));
        reg.setPendingTeleportManager(new PendingTeleportManager());
        reg.setRtpManager(new RtpManager(plugin));
        reg.setTpaRuntimeManager(new TpaRuntimeManager());
        reg.setNetworkPlayerNameCache(new NetworkPlayerNameCache());
        reg.setRandomRespawnManager(new RandomRespawnManager());
        reg.setRandomRespawnService(new RandomRespawnService(plugin));
        reg.setPingService(new PingService());

        StorageManager storage = new StorageManager(plugin, versionAdapter);
        storage.load();
        reg.setStorageManager(storage);
        reg.setTeleportService(new TeleportService(plugin, reg.pendingTeleportManager(), storage, versionAdapter));

        DatabaseManager db;
        try {
            db = DatabaseManager.open(settings.databaseConfig());
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            Bukkit.getPluginManager().disablePlugin(plugin);
            return null;
        }
        reg.setDatabaseManager(db);
        reg.setSpawns(new SpawnService(plugin, new SpawnDirectory(db, new SpawnRepository(db.dataSource())), routes, storage, hubCommand));
        reg.spawns().apply(settings.serverName(), routes, null).exceptionally(error -> {
            plugin.getLogger().severe("Could not publish spawns.yml: " + error.getMessage());
            return null;
        });

        reg.setPlayerService(new PlayerService(db, new PlayerRepository(db.dataSource()), new PlayerServerCache()));
        reg.setWarpService(new WarpService(db, new WarpRepository(db.dataSource()), new WarpCache()));
        reg.setPlayerWarpService(new PlayerWarpService(db, new PlayerWarpRepository(db.dataSource())));
        reg.setTpaService(new TpaService(db, new TpaRepository(db.dataSource()), new TpaRequestCache()));
        reg.setHomeService(new HomeService(db, new HomeRepository(db.dataSource())));
        reg.setLastLocationService(new LastLocationService(db, new LastLocationRepository(db.dataSource())));
        reg.warpService().refreshCache();

        BackLocationManager backManager = new BackLocationManager();
        reg.setBackLocationManager(backManager);
        TeleportEffectPlayer effectPlayer = new TeleportEffectPlayer(settings);
        reg.setTeleportEffectPlayer(effectPlayer);
        reg.teleportService().wire(reg.messageService(), reg.auditLogger(), reg.rtpManager(), effectPlayer, backManager, settings.serverName());

        PaperMessagingManager messaging = new PaperMessagingManager(plugin, settings.dedupeWindowMs(),
                settings.requestTimeoutMs(), () -> reg.settings().serverName());
        messaging.register();
        messaging.setHandler(new PacketHandler(plugin, reg));
        reg.setMessaging(messaging);

        plugin.getServer().getPluginManager().registerEvents(new PaperPlayerListener(plugin), plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new RespawnListener(plugin, reg.randomRespawnManager(), reg.randomRespawnService()), plugin);
        registerCommands(plugin, reg);
        startScheduledTasks(plugin, reg);

        net.kyori.adventure.text.logger.slf4j.ComponentLogger clog = plugin.getComponentLogger();
        clog.info(MINI.deserialize(
                "<green>Startup complete! <gray>Server: <aqua>" + reg.settings().serverName()
                        + " <gray>| Adapter: <aqua>" + versionAdapter.getClass().getSimpleName()
                        + " <gray>| Language: <aqua>" + reg.settings().language()));

        return reg;
    }

    public static void shutdown(NetworkPaperPlugin plugin, ServiceRegistry reg) {
        if (reg == null) return;
        if (reg.messaging() != null) reg.messaging().shutdown();
        if (reg.databaseManager() != null) reg.databaseManager().shutdown();

        net.kyori.adventure.text.logger.slf4j.ComponentLogger clog = plugin.getComponentLogger();
        clog.info(MINI.deserialize("<red>TeleHop <gray>has been <red>disabled<gray>. Goodbye!"));
    }

    public static java.util.concurrent.CompletableFuture<Void> reload(NetworkPaperPlugin plugin, ServiceRegistry reg) {
        SpawnRoutes routes = SpawnRoutes.load(plugin.getDataFolder());
        PaperSettings settings = PaperSettings.load(plugin);
        initLanguageFiles(plugin);
        var messages = buildMessageService(plugin, settings);
        return reg.spawns().apply(settings.serverName(), routes, null).thenRun(() -> {
            reg.setSettings(settings);
            reg.setMessageService(messages);
            reg.warpService().refreshCache();
            reg.setAuditLogger(new AuditLogger(plugin, settings.auditEnabled()));
            plugin.getLogger().info("Reloaded TeleHop settings and spawn destinations.");
        });
    }

    // ── internal helpers ─────────────────────────────────────────────

    static void initConfigFiles(NetworkPaperPlugin plugin) {
        for (String template : CONFIG_TEMPLATES) {
            File target = new File(plugin.getDataFolder(), template);
            if (!target.exists()) {
                target.getParentFile().mkdirs();
                plugin.saveResource(template, false);
            }
        }
    }

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
        try (var stream = plugin.getResource("languages/" + lang + ".yml")) {
            if (stream != null) {
                try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    langFile.setDefaults(YamlConfiguration.loadConfiguration(reader));
                }
            }
        } catch (IOException ex) { throw new IllegalStateException("Cannot read bundled translation", ex); }
        FileConfiguration fallback = YamlConfiguration.loadConfiguration(new File(langDir, "en.yml"));
        // Existing installations keep customised language files across upgrades.
        try (var reader = new InputStreamReader(plugin.getResource("languages/en.yml"), StandardCharsets.UTF_8)) {
            fallback.setDefaults(YamlConfiguration.loadConfiguration(reader));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read bundled English messages", e);
        }
        return new MessageService(langFile, fallback, plugin.commandNames());
    }

    private static void registerCommands(NetworkPaperPlugin plugin, ServiceRegistry reg) {
        PaperCommandManager manager = new PaperCommandManager(plugin);
        manager.enableUnstableAPI("brigadier");
        manager.enableUnstableAPI("help");
        plugin.commandNames().register(manager.getCommandReplacements());

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

        manager.getCommandCompletions().registerCompletion("spawns", c -> reg.spawns().suggestions(c.getPlayer()));
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
        if (reg.spawns().hubCommand()) manager.registerCommand(new HubCommand(plugin));
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
        manager.registerCommand(new HomeCommand(plugin));
        manager.registerCommand(new SetHomeCommand(plugin));
        manager.registerCommand(new DelHomeCommand(plugin));
        manager.registerCommand(new BackCommand(plugin));
        manager.registerCommand(new TpaToggleCommand(plugin));
        manager.registerCommand(new ForceDelHomeCommand(plugin));
        manager.registerCommand(new LastLocationCommand(plugin));
        manager.registerCommand(new ForceLastLocCommand(plugin));
        manager.registerCommand(new ForceSetHomeCommand(plugin));
        manager.registerCommand(new ListHomesCommand(plugin));
        manager.registerCommand(new PlayerInfoCommand(plugin));
    }

    private static void startScheduledTasks(NetworkPaperPlugin plugin, ServiceRegistry reg) {
        Bukkit.getScheduler().runTaskTimer(plugin, reg.spawns()::refreshSuggestions, 1L, 200L);
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

    private static void printBanner(NetworkPaperPlugin plugin) {
        net.kyori.adventure.text.logger.slf4j.ComponentLogger log = plugin.getComponentLogger();
        log.info(MINI.deserialize(""));
        log.info(MINI.deserialize("<gradient:#00d4ff:#a855f7:#ff6b9d>  ████████╗███████╗██╗     ███████╗██╗  ██╗ ██████╗ ██████╗</gradient>"));
        log.info(MINI.deserialize("<gradient:#00d4ff:#a855f7:#ff6b9d>  ╚══██╔══╝██╔════╝██║     ██╔════╝██║  ██║██╔═══██╗██╔══██╗</gradient>"));
        log.info(MINI.deserialize("<gradient:#00d4ff:#a855f7:#ff6b9d>     ██║   █████╗  ██║     █████╗  ███████║██║   ██║██████╔╝</gradient>"));
        log.info(MINI.deserialize("<gradient:#00d4ff:#a855f7:#ff6b9d>     ██║   ██╔══╝  ██║     ██╔══╝  ██╔══██║██║   ██║██╔═══╝</gradient>"));
        log.info(MINI.deserialize("<gradient:#00d4ff:#a855f7:#ff6b9d>     ██║   ███████╗███████╗███████╗██║  ██║╚██████╔╝██║</gradient>"));
        log.info(MINI.deserialize("<gradient:#00d4ff:#a855f7:#ff6b9d>     ╚═╝   ╚══════╝╚══════╝╚══════╝╚═╝  ╚═╝ ╚═════╝ ╚═╝</gradient>"));
        log.info(MINI.deserialize(""));
        log.info(MINI.deserialize("  <aqua>Version: <white>" + plugin.getPluginMeta().getVersion()
                + "  <gray>|  <aqua>Platform: <white>Paper"
                + "  <gray>|  <aqua>MC: <white>" + detectMinecraftVersion(plugin)));
        log.info(MINI.deserialize("  <aqua>Author: <white>Epildev  <gray>|  <aqua>Website: <white>developer.epildevconnect.uk"));
        log.info(MINI.deserialize(""));
    }

    private static String detectMinecraftVersion(NetworkPaperPlugin plugin) {
        try {
            return (String) plugin.getServer().getClass()
                    .getMethod("getMinecraftVersion").invoke(plugin.getServer());
        } catch (Exception e) {
            String ver = Bukkit.getVersion();
            Matcher m = Pattern.compile("\\(MC: ([\\d.]+)\\)").matcher(ver);
            if (m.find()) {
                return m.group(1);
            }
            return Bukkit.getBukkitVersion().split("-")[0];
        }
    }

    private static VersionAdapter resolveVersionAdapter(NetworkPaperPlugin plugin) {
        String mcVersion = detectMinecraftVersion(plugin);
        plugin.getLogger().info("Minecraft version: " + mcVersion);

        if (mcVersion.startsWith("26.")) {
            try {
                return (VersionAdapter) Class.forName("com.telehop.paper.v26_1.Paper261Adapter")
                        .getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                plugin.getLogger().severe("Failed to load Paper 26.1.x adapter: " + e.getMessage());
                return null;
            }
        }

        if (mcVersion.startsWith("1.21")) {
            try {
                return (VersionAdapter) Class.forName("com.telehop.paper.v1_21.Paper121Adapter")
                        .getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                plugin.getLogger().severe("Failed to load Paper 1.21.x adapter: " + e.getMessage());
                return null;
            }
        }

        return null;
    }
}
