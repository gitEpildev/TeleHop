package com.telehop.velocity;

import com.telehop.common.db.DatabaseManager;
import com.telehop.common.db.PlayerRepository;
import com.telehop.common.service.PlayerServerCache;
import com.telehop.common.service.PlayerService;
import com.telehop.velocity.config.VelocitySettings;
import com.telehop.velocity.config.VelocityProfiles;
import com.telehop.common.config.DeploymentConfig;
import com.telehop.velocity.handler.VelocityPacketHandler;
import com.telehop.velocity.messaging.RedisCrossProxyBridge;
import com.telehop.velocity.messaging.VelocityMessagingManager;
import com.telehop.velocity.service.PendingActionManager;
import com.telehop.velocity.service.ServerPingMonitor;
import com.telehop.velocity.service.VelocityPlayerTracker;
import com.telehop.velocity.service.VelocityServiceRegistry;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Performs the full proxy-plugin startup sequence and constructs the
 * {@link VelocityServiceRegistry}. Keeps {@link NetworkVelocityPlugin}
 * thin, lifecycle only.
 */
public final class VelocityBootstrap {

    private VelocityBootstrap() {}

    public static VelocityServiceRegistry init(NetworkVelocityPlugin plugin,
                                               ProxyServer proxy,
                                               Logger logger,
                                               Path dataDirectory) throws Exception {
        printBanner(logger, proxy.getPluginManager().fromInstance(plugin).orElseThrow()
                .getDescription().getVersion().orElseThrow());
        String runtime = proxy.getVersion().getVersion();
        if (!runtime.matches("[34]\\..*")) throw new IllegalStateException("Unsupported Velocity version " + runtime);
        VelocityServiceRegistry reg = new VelocityServiceRegistry();

        var names = new LinkedHashSet<String>();
        for (String name : proxy.getConfiguration().getAttemptConnectionOrder()) {
            if (proxy.getServer(name).isPresent()) names.add(name);
        }
        proxy.getAllServers().stream().map(server -> server.getServerInfo().getName()).sorted().forEach(names::add);
        boolean first = !java.nio.file.Files.exists(dataDirectory.resolve("migrations/state.yml"));
        VelocitySettings settings = VelocityProfiles.load(dataDirectory, List.copyOf(names));
        DeploymentConfig deployment = DeploymentConfig.read(dataDirectory);
        if (deployment.logPlatform()) logger.info("Using Velocity {} shared messaging API (runtime {})", runtime.charAt(0), runtime);
        if (first && deployment.logMigration()) logger.info("Prepared Velocity schema 1. Recovery records are in migrations/.");
        if (deployment.logValidation()) logger.info("Validated the {} deployment profile", deployment.mode());
        if (deployment.debug()) logger.info("Active configuration directory: {}", deployment.profileDirectory(dataDirectory));
        reg.setSettings(settings);

        DatabaseManager db = DatabaseManager.open(settings.databaseConfig());
        reg.setDatabaseManager(db);

        try {
            registerNetwork(plugin, proxy, logger, reg);
            return reg;
        } catch (RuntimeException failure) {
            shutdown(reg, logger);
            throw failure;
        }
    }

    private static void registerNetwork(NetworkVelocityPlugin plugin, ProxyServer proxy, Logger logger,
                                        VelocityServiceRegistry reg) {
        var settings = reg.settings();
        var db = reg.databaseManager();
        VelocityPlayerTracker tracker = new VelocityPlayerTracker(
                new PlayerService(db, new PlayerRepository(db.dataSource()), new PlayerServerCache())
        );
        reg.setPlayerTracker(tracker);

        reg.setPendingActionManager(new PendingActionManager());

        if (settings.multiProxyEnabled()) {
            logger.info("Multi-proxy mode enabled (proxyId={}). Initializing Redis bridge...",
                    settings.redisConfig().proxyId());
            RedisCrossProxyBridge bridge = new RedisCrossProxyBridge(settings.redisConfig(), logger, settings.dedupeWindowMs());
            reg.setRedisBridge(bridge);
            bridge.start();
            tracker.setRedisBridge(bridge);
        }

        VelocityMessagingManager messaging = new VelocityMessagingManager(
                proxy, settings.dedupeWindowMs(), settings.requestTimeoutMs());
        reg.setMessaging(messaging);
        messaging.register();
        VelocityPacketHandler packetHandler = new VelocityPacketHandler(plugin, proxy, logger, reg);
        messaging.setHandler(packetHandler);
        proxy.getEventManager().register(plugin, messaging);
        reg.setPacketHandler(packetHandler);

        if (reg.redisBridge() != null) {
            reg.redisBridge().setPacketHandler(packetHandler::handle);
        }

        ServerPingMonitor pingMonitor = new ServerPingMonitor(
                plugin, proxy, messaging, settings.backends(), logger);
        reg.setPingMonitor(pingMonitor);
        pingMonitor.start();

        logger.info("\u001b[32mStartup complete! \u001b[90mMulti-proxy: \u001b[36m{}\u001b[0m",
                settings.multiProxyEnabled() ? "enabled" : "disabled");
    }

    public static void shutdown(VelocityServiceRegistry reg, Logger logger) {
        if (reg == null) return;
        if (reg.pingMonitor() != null) reg.pingMonitor().shutdown();
        if (reg.redisBridge() != null) reg.redisBridge().shutdown();
        if (reg.messaging() != null) reg.messaging().shutdown();
        if (reg.databaseManager() != null) reg.databaseManager().shutdown();
        logger.info("\u001b[31mTeleHop \u001b[90mhas been \u001b[31mdisabled\u001b[90m. Goodbye!\u001b[0m");
    }

    private static void printBanner(Logger logger, String version) {
        String cyan = "\u001b[36m";
        String purple = "\u001b[35m";
        String white = "\u001b[37m";
        String grey = "\u001b[90m";
        String reset = "\u001b[0m";

        logger.info("");
        logger.info("{}  ████████╗███████╗██╗     ███████╗██╗  ██╗ ██████╗ ██████╗{}", cyan, reset);
        logger.info("{}  ╚══██╔══╝██╔════╝██║     ██╔════╝██║  ██║██╔═══██╗██╔══██╗{}", cyan, reset);
        logger.info("{}     ██║   █████╗  ██║     █████╗  ███████║██║   ██║██████╔╝{}", purple, reset);
        logger.info("{}     ██║   ██╔══╝  ██║     ██╔══╝  ██╔══██║██║   ██║██╔═══╝{}", purple, reset);
        logger.info("{}     ██║   ███████╗███████╗███████╗██║  ██║╚██████╔╝██║{}", purple, reset);
        logger.info("{}     ╚═╝   ╚══════╝╚══════╝╚══════╝╚═╝  ╚═╝ ╚═════╝ ╚═╝{}", cyan, reset);
        logger.info("");
        logger.info("  {}Version: {}{} {}| {}Platform: {}Velocity {}| {}Author: {}Epildev{}",
                cyan, white, version, grey, cyan, white, grey, cyan, white, reset);
        logger.info("");
    }

}
