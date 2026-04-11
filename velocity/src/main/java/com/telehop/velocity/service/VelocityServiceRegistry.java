package com.telehop.velocity.service;

import com.telehop.common.db.DatabaseManager;
import com.telehop.velocity.config.VelocitySettings;
import com.telehop.velocity.handler.VelocityPacketHandler;
import com.telehop.velocity.messaging.VelocityMessagingManager;

/**
 * Central holder for every service and manager in the Velocity proxy plugin.
 * Mirrors the Paper-side {@code ServiceRegistry} pattern.
 */
public final class VelocityServiceRegistry {
    private VelocitySettings settings;
    private DatabaseManager databaseManager;
    private VelocityPlayerTracker playerTracker;
    private VelocityMessagingManager messaging;
    private PendingActionManager pendingActionManager;
    private VelocityPacketHandler packetHandler;

    public VelocitySettings settings() { return settings; }
    public void setSettings(VelocitySettings settings) { this.settings = settings; }

    public DatabaseManager databaseManager() { return databaseManager; }
    public void setDatabaseManager(DatabaseManager databaseManager) { this.databaseManager = databaseManager; }

    public VelocityPlayerTracker playerTracker() { return playerTracker; }
    public void setPlayerTracker(VelocityPlayerTracker playerTracker) { this.playerTracker = playerTracker; }

    public VelocityMessagingManager messaging() { return messaging; }
    public void setMessaging(VelocityMessagingManager messaging) { this.messaging = messaging; }

    public PendingActionManager pendingActionManager() { return pendingActionManager; }
    public void setPendingActionManager(PendingActionManager pendingActionManager) { this.pendingActionManager = pendingActionManager; }

    public VelocityPacketHandler packetHandler() { return packetHandler; }
    public void setPacketHandler(VelocityPacketHandler packetHandler) { this.packetHandler = packetHandler; }
}
