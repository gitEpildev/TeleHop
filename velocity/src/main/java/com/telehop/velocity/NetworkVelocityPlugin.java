package com.telehop.velocity;

import com.telehop.velocity.service.VelocityServiceRegistry;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.nio.file.Path;

/**
 * Velocity proxy plugin entry point. Lifecycle only. All wiring lives in
 * {@link VelocityBootstrap}, all services in {@link VelocityServiceRegistry},
 * and packet handling in {@link com.telehop.velocity.handler.VelocityPacketHandler}.
 */
@Plugin(
        id = "telehop-velocity",
        name = "TeleHop-Velocity",
        version = "3.0.1",
        url = "https://developer.epildevconnect.uk",
        description = "Cross-server teleportation proxy bridge for TeleHop",
        authors = {"Epildev"}
)
public class NetworkVelocityPlugin {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private VelocityServiceRegistry services;

    @Inject
    public NetworkVelocityPlugin(ProxyServer proxy, Logger logger,
                                 @com.velocitypowered.api.plugin.annotation.DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInit(ProxyInitializeEvent event) {
        try {
            services = VelocityBootstrap.init(this, proxy, logger, dataDirectory);
        } catch (Exception e) {
            logger.error("Failed to initialize TeleHop-Velocity", e);
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        VelocityBootstrap.shutdown(services, logger);
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        if (services == null) return;
        event.getPlayer().getCurrentServer().ifPresent(conn ->
                services.playerTracker().update(event.getPlayer(), conn.getServerInfo().getName()));
    }

    @Subscribe
    public void onServerSwitch(ServerPostConnectEvent event) {
        if (services == null) return;
        event.getPlayer().getCurrentServer().ifPresent(joined -> {
            services.playerTracker().update(event.getPlayer(), joined.getServerInfo().getName());
            services.packetHandler().executePendingAction(event.getPlayer(), joined);
        });
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        if (services == null) return;
        services.pendingActionManager().remove(event.getPlayer().getUniqueId());
        services.playerTracker().remove(event.getPlayer());
    }
}
