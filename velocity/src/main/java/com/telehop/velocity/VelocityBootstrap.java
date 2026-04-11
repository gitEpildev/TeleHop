package com.telehop.velocity;

import com.telehop.common.db.DatabaseManager;
import com.telehop.common.db.PlayerRepository;
import com.telehop.common.service.PlayerServerCache;
import com.telehop.common.service.PlayerService;
import com.telehop.velocity.config.VelocitySettings;
import com.telehop.velocity.handler.VelocityPacketHandler;
import com.telehop.velocity.messaging.VelocityMessagingManager;
import com.telehop.velocity.service.PendingActionManager;
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
 * thin — lifecycle only.
 */
public final class VelocityBootstrap {

    private VelocityBootstrap() {}

    public static VelocityServiceRegistry init(NetworkVelocityPlugin plugin,
                                               ProxyServer proxy,
                                               Logger logger,
                                               Path dataDirectory) throws Exception {
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

        VelocityMessagingManager messaging = new VelocityMessagingManager(
                proxy, settings.dedupeWindowMs(), settings.requestTimeoutMs());
        messaging.register();
        VelocityPacketHandler packetHandler = new VelocityPacketHandler(plugin, proxy, logger, reg);
        messaging.setHandler(packetHandler);
        proxy.getEventManager().register(plugin, messaging);
        reg.setMessaging(messaging);
        reg.setPacketHandler(packetHandler);

        logger.info("TeleHop-Velocity enabled.");
        return reg;
    }

    public static void shutdown(VelocityServiceRegistry reg) {
        if (reg == null) return;
        if (reg.messaging() != null) reg.messaging().shutdown();
        if (reg.databaseManager() != null) reg.databaseManager().shutdown();
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
