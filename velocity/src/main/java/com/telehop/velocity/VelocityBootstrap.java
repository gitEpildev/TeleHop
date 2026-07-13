package com.telehop.velocity;

import com.telehop.common.db.DatabaseManager;
import com.telehop.common.db.PlayerRepository;
import com.telehop.common.service.PlayerServerCache;
import com.telehop.common.service.PlayerService;
import com.telehop.velocity.config.VelocitySettings;
import com.telehop.velocity.handler.VelocityPacketHandler;
import com.telehop.velocity.messaging.RedisCrossProxyBridge;
import com.telehop.velocity.messaging.VelocityMessagingManager;
import com.telehop.velocity.service.PendingActionManager;
import com.telehop.velocity.service.ServerPingMonitor;
import com.telehop.velocity.service.VelocityPlayerTracker;
import com.telehop.velocity.service.VelocityServiceRegistry;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Performs the full proxy-plugin startup sequence and constructs the
 * {@link VelocityServiceRegistry}. Keeps {@link NetworkVelocityPlugin}
 * thin, lifecycle only.
 */
public final class VelocityBootstrap {

    private VelocityBootstrap() {}

    private static final String PLUGIN_VERSION = "2.1.0";

    public static VelocityServiceRegistry init(NetworkVelocityPlugin plugin,
                                               ProxyServer proxy,
                                               Logger logger,
                                               Path dataDirectory) throws Exception {
        printBanner(logger);
        VelocityServiceRegistry reg = new VelocityServiceRegistry();

        VelocitySettings settings = loadSettings(plugin, dataDirectory);
        reg.setSettings(settings);

        DatabaseManager db = new DatabaseManager(settings.databaseConfig());
        db.initSchema();
        reg.setDatabaseManager(db);

        VelocityPlayerTracker tracker = new VelocityPlayerTracker(
                new PlayerService(db, new PlayerRepository(db.dataSource()), new PlayerServerCache())
        );
        reg.setPlayerTracker(tracker);

        reg.setPendingActionManager(new PendingActionManager());

        if (settings.multiProxyEnabled()) {
            logger.info("Multi-proxy mode enabled (proxyId={}). Initializing Redis bridge...",
                    settings.redisConfig().proxyId());
            RedisCrossProxyBridge bridge = new RedisCrossProxyBridge(settings.redisConfig(), logger);
            bridge.start();
            reg.setRedisBridge(bridge);
            tracker.setRedisBridge(bridge);
        }

        VelocityMessagingManager messaging = new VelocityMessagingManager(
                proxy, settings.dedupeWindowMs(), settings.requestTimeoutMs());
        messaging.register();
        VelocityPacketHandler packetHandler = new VelocityPacketHandler(plugin, proxy, logger, reg);
        messaging.setHandler(packetHandler);
        proxy.getEventManager().register(plugin, messaging);
        reg.setMessaging(messaging);
        reg.setPacketHandler(packetHandler);

        if (reg.redisBridge() != null) {
            reg.redisBridge().setPacketHandler(packetHandler::handle);
        }

        ServerPingMonitor pingMonitor = new ServerPingMonitor(
                plugin, proxy, messaging, settings.backends(), logger);
        pingMonitor.start();
        reg.setPingMonitor(pingMonitor);

        logger.info("\u001b[32mStartup complete! \u001b[90mMulti-proxy: \u001b[36m{}\u001b[0m",
                settings.multiProxyEnabled() ? "enabled" : "disabled");
        return reg;
    }

    public static void shutdown(VelocityServiceRegistry reg, Logger logger) {
        if (reg == null) return;
        if (reg.pingMonitor() != null) reg.pingMonitor().shutdown();
        if (reg.redisBridge() != null) reg.redisBridge().shutdown();
        if (reg.messaging() != null) reg.messaging().shutdown();
        if (reg.databaseManager() != null) reg.databaseManager().shutdown();
        logger.info("\u001b[31mTeleHop \u001b[90mhas been \u001b[31mdisabled\u001b[90m. Goodbye!\u001b[0m");
    }

    private static void printBanner(Logger logger) {
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
                cyan, white, PLUGIN_VERSION, grey, cyan, white, grey, cyan, white, reset);
        logger.info("");
    }

    private static VelocitySettings loadSettings(NetworkVelocityPlugin plugin,
                                                  Path dataDirectory) throws IOException {
        Files.createDirectories(dataDirectory);
        Path config = dataDirectory.resolve("config.properties");
        if (Files.notExists(config)) {
            try (InputStream in = plugin.getClass().getClassLoader().getResourceAsStream("config.properties")) {
                if (in != null) {
                    Files.copy(in, config);
                }
            }
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(config)) {
            props.load(in);
        }
        return VelocitySettings.from(props);
    }
}
