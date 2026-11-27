package com.telehop.paper;

import com.telehop.paper.platform.PlatformText;
import co.aikar.commands.BukkitCommandManager;
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
import com.telehop.paper.command.TravelCommandRegistry;
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
import com.telehop.common.config.DeploymentConfig;
import com.telehop.paper.config.PaperProfiles;
import com.telehop.paper.config.LanguageFiles;
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
import org.bukkit.entity.Player;

import java.io.IOException;

/**
 * Performs the full plugin startup sequence and constructs the {@link ServiceRegistry}.
 * Keeps {@link NetworkPaperPlugin} thin, lifecycle only.
 */
public final class Bootstrap {
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private Bootstrap() {}

    public static ServiceRegistry init(NetworkPaperPlugin plugin) {
        VersionAdapter versionAdapter = plugin.platform().version();
        printBanner(plugin);
        plugin.setVersionAdapter(versionAdapter);

        DeploymentConfig configuration = PaperProfiles.initialise(plugin);
        plugin.setConfiguration(configuration);
        if (configuration.logPlatform()) plugin.getLogger().info("Platform detection completed: " + plugin.platform().description());
        if (configuration.debug()) plugin.getLogger().info("Active configuration directory: " + plugin.profileDirectory());
        plugin.getLogger().info("Deployment profile: " + configuration.mode() + " (" + plugin.profileDirectory() + ")");
        SpawnRoutes routes = SpawnRoutes.loadProfile(plugin.profileDirectory());
        PaperSettings settings = PaperSettings.load(plugin.getDataFolder().toPath(), configuration);
        boolean hubCommand = settings.isFeatureEnabled("hub");
        plugin.setCommandNames(CommandNames.load(plugin, settings, plugin.profileDirectory().toFile(),
                configuration.customNames(), configuration.additionalCommands()));
        TravelCommandRegistry.checkOwnership(plugin, plugin.commandNames());
        ServiceRegistry reg = new ServiceRegistry();
        reg.setSettings(settings);

        reg.setMessageService(buildMessageService(plugin, settings, plugin.commandNames()));
        reg.setPermissionService(PermissionService.create(plugin));
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
            db = DatabaseManager.start(settings.databaseConfig(), plugin.getLogger());
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
        messaging.register(settings.networkMessagingEnabled());
        messaging.setHandler(new PacketHandler(plugin, reg));
        reg.setMessaging(messaging);

        plugin.getServer().getPluginManager().registerEvents(new PaperPlayerListener(plugin), plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new RespawnListener(plugin, reg.randomRespawnManager(), reg.randomRespawnService()), plugin);
        registerCommands(plugin, reg);
        startScheduledTasks(plugin, reg);

        db.schemaReady().whenComplete((ready, failure) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (failure != null) {
                    plugin.getLogger().severe("Database startup failed. Check config/database.yml, connectivity and schema permissions.");
                    Bukkit.getPluginManager().disablePlugin(plugin);
                } else {
                    plugin.getLogger().info("Startup complete! Server: " + reg.settings().serverName()
                            + " | Adapter: " + versionAdapter.getClass().getSimpleName()
                            + " | Language: " + reg.settings().language());
                }
            });
        });

        return reg;
    }

    public static void shutdown(NetworkPaperPlugin plugin, ServiceRegistry reg) {
        if (reg == null) return;
        if (reg.messaging() != null) reg.messaging().shutdown();
        if (reg.databaseManager() != null) reg.databaseManager().shutdown();

        plugin.getLogger().info("TeleHop has been disabled.");
    }

    private record Candidate(DeploymentConfig deployment, PaperSettings settings, SpawnRoutes routes,
                             CommandNames names, MessageService messages) {}

    /** Inspect all reloadable files without changing live services or publishing routes. */
    public static void validateConfiguration(NetworkPaperPlugin plugin) {
        candidate(plugin);
    }

    /** Replace only the loaded locale and fallback after both pass validation. */
    public static void reloadMessages(NetworkPaperPlugin plugin) {
        if (plugin.reloadInProgress()) throw new IllegalArgumentException("A configuration reload is already running");
        var messages = buildMessageService(plugin, plugin.settings(), plugin.commandNames());
        plugin.services().setMessageService(messages);
        plugin.services().teleportService().setMessageService(messages);
    }

    private static Candidate candidate(NetworkPaperPlugin plugin) {
        var data = plugin.getDataFolder().toPath();
        final DeploymentConfig deployment;
        try { deployment = DeploymentConfig.read(data); }
        catch (IOException ex) { throw new IllegalArgumentException("Cannot read config.yml", ex); }
        if (deployment.mode() != plugin.configuration().mode()) throw new IllegalArgumentException("Restart the backend after changing deployment mode");
        var settings = PaperSettings.load(data, deployment);
        var current = plugin.settings();
        if (!current.databaseConfig().equals(settings.databaseConfig()) || !current.serverName().equals(settings.serverName())
                || current.requestTimeoutMs() != settings.requestTimeoutMs() || current.dedupeWindowMs() != settings.dedupeWindowMs()
                || current.networkMessagingEnabled() != settings.networkMessagingEnabled() || current.multiProxyEnabled() != settings.multiProxyEnabled()) {
            throw new IllegalArgumentException("Restart the backend to apply database, identity or messaging changes");
        }
        var routes = SpawnRoutes.loadProfile(plugin.profileDirectory());
        var names = CommandNames.load(plugin, settings, plugin.profileDirectory().toFile(),
                deployment.customNames(), deployment.additionalCommands(), plugin.services().spawns().hubCommand());
        TravelCommandRegistry.checkOwnership(plugin, names);
        if (!plugin.commandNames().sameRegistration(names)) throw new IllegalArgumentException("Restart the backend to register changed command names or aliases");
        return new Candidate(deployment, settings, routes, names, buildMessageService(plugin, settings, names));
    }

    /** Validate command files against the running infrastructure before swapping their immutable snapshot. */
    public static void commandRules(NetworkPaperPlugin plugin, boolean apply) {
        if (apply && plugin.reloadInProgress()) throw new IllegalArgumentException("A configuration reload is already running");
        final DeploymentConfig deployment;
        try { deployment = DeploymentConfig.read(plugin.getDataFolder().toPath()); }
        catch (IOException ex) { throw new IllegalArgumentException("Cannot read config.yml", ex); }
        if (deployment.mode() != plugin.configuration().mode()) throw new IllegalArgumentException("Restart after changing the deployment mode");
        var next = CommandNames.load(plugin, plugin.settings(), plugin.profileDirectory().toFile(),
                deployment.customNames(), deployment.additionalCommands(), plugin.services().spawns().hubCommand());
        TravelCommandRegistry.checkOwnership(plugin, next);
        if (apply) {
            if (!plugin.commandNames().sameRegistration(next)) throw new IllegalArgumentException("Restart to register changed command labels");
            plugin.setCommandNames(next);
            plugin.getServer().getOnlinePlayers().forEach(Player::updateCommands);
        }
    }

    public static java.util.concurrent.CompletableFuture<Void> reload(NetworkPaperPlugin plugin, ServiceRegistry reg) {
        Candidate next = candidate(plugin);
        return reg.spawns().apply(next.settings().serverName(), next.routes(), null).thenRun(() -> {
            plugin.setConfiguration(next.deployment());
            plugin.setCommandNames(next.names());
            reg.setSettings(next.settings());
            reg.setMessageService(next.messages());
            reg.setAuditLogger(new AuditLogger(plugin, next.settings().auditEnabled()));
            var effects = new TeleportEffectPlayer(next.settings());
            reg.setTeleportEffectPlayer(effects);
            reg.teleportService().wire(next.messages(), reg.auditLogger(), reg.rtpManager(), effects, reg.backLocationManager(), next.settings().serverName());
            reg.warpService().refreshCache();
            plugin.getServer().getOnlinePlayers().forEach(Player::updateCommands);
            if (next.deployment().logValidation()) plugin.getLogger().info("Reloaded TeleHop settings and spawn destinations.");
        });
    }

    static MessageService buildMessageService(NetworkPaperPlugin plugin, PaperSettings settings, CommandNames names) {
        var data = plugin.getDataFolder().toPath();
        var fallback = LanguageFiles.load(data, "en", plugin);
        var messages = settings.language().equals("en") ? fallback : LanguageFiles.load(data, settings.language(), plugin);
        return new MessageService(messages, fallback, names);
    }

    private static void registerCommands(NetworkPaperPlugin plugin, ServiceRegistry reg) {
        BukkitCommandManager manager = plugin.platform().capabilities().asynchronousTeleport()
                ? new PaperCommandManager(plugin) : new BukkitCommandManager(plugin);
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
                        .filter(warp -> c.getPlayer() != null && reg.permissionService().warp(c.getPlayer(), warp, reg.settings().serverName()))
                        .map(com.telehop.common.model.WarpRecord::name).toList());
        try {
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
            TravelCommandRegistry.register(plugin, manager, reg.playerWarpService());
        } catch (RuntimeException error) {
            manager.unregisterCommands();
            throw error;
        }
    }

    private static void startScheduledTasks(NetworkPaperPlugin plugin, ServiceRegistry reg) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            reg.spawns().refreshSuggestions();
            reg.rtpManager().expire();
        }, 1L, 200L);
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () ->
                reg.tpaService().expiredNow(reg.settings().tpaTimeoutSeconds()).thenAccept(expired -> {
                    expired.forEach(reg.tpaService()::delete);
                    var local = expired.stream().filter(reg.tpaRuntimeManager()::removeRequest).toList();
                    if (!local.isEmpty()) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            for (var request : local) {
                                Player sender = Bukkit.getPlayer(request.senderUuid());
                                if (sender != null) PlatformText.send(sender, plugin.msg("request-expired"));
                                Player target = Bukkit.getPlayer(request.targetUuid());
                                if (target != null) PlatformText.send(target, plugin.msg("request-expired"));
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
        java.util.function.Consumer<net.kyori.adventure.text.Component> log = component ->
                plugin.getServer().getConsoleSender().sendMessage(com.telehop.paper.platform.PlatformText.legacy(component));
        log.accept(MINI.deserialize(""));
        log.accept(MINI.deserialize("<gradient:#00d4ff:#a855f7:#ff6b9d>  ████████╗███████╗██╗     ███████╗██╗  ██╗ ██████╗ ██████╗</gradient>"));
        log.accept(MINI.deserialize("<gradient:#00d4ff:#a855f7:#ff6b9d>  ╚══██╔══╝██╔════╝██║     ██╔════╝██║  ██║██╔═══██╗██╔══██╗</gradient>"));
        log.accept(MINI.deserialize("<gradient:#00d4ff:#a855f7:#ff6b9d>     ██║   █████╗  ██║     █████╗  ███████║██║   ██║██████╔╝</gradient>"));
        log.accept(MINI.deserialize("<gradient:#00d4ff:#a855f7:#ff6b9d>     ██║   ██╔══╝  ██║     ██╔══╝  ██╔══██║██║   ██║██╔═══╝</gradient>"));
        log.accept(MINI.deserialize("<gradient:#00d4ff:#a855f7:#ff6b9d>     ██║   ███████╗███████╗███████╗██║  ██║╚██████╔╝██║</gradient>"));
        log.accept(MINI.deserialize("<gradient:#00d4ff:#a855f7:#ff6b9d>     ╚═╝   ╚══════╝╚══════╝╚══════╝╚═╝  ╚═╝ ╚═════╝ ╚═╝</gradient>"));
        log.accept(MINI.deserialize(""));
        log.accept(MINI.deserialize("  <aqua>Version: <white>" + plugin.getDescription().getVersion()
                + "  <gray>|  <aqua>Platform: <white>" + plugin.platform().implementation()
                + "  <gray>|  <aqua>MC: <white>" + plugin.getServer().getBukkitVersion().split("-", 2)[0]));
        log.accept(MINI.deserialize("  <aqua>Author: <white>Epildev  <gray>|  <aqua>Website: <white>developer.epildevconnect.uk"));
        log.accept(MINI.deserialize(""));
    }

}
