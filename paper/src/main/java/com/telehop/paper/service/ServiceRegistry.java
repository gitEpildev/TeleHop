package com.telehop.paper.service;

import com.telehop.common.db.DatabaseManager;
import com.telehop.common.service.HomeService;
import com.telehop.common.service.PlayerService;
import com.telehop.common.service.PlayerWarpService;
import com.telehop.common.service.TpaService;
import com.telehop.common.service.WarpService;
import com.telehop.paper.config.PaperSettings;
import com.telehop.paper.config.StorageManager;
import com.telehop.paper.messaging.PaperMessagingManager;

/**
 * Central holder for every service and manager created during bootstrap.
 * Avoids scattering dozens of getters across the plugin main class.
 */
public final class ServiceRegistry {
    private PaperSettings settings;
    private MessageService messageService;
    private PermissionService permissionService;
    private AuditLogger auditLogger;
    private DatabaseManager databaseManager;
    private PlayerService playerService;
    private WarpService warpService;
    private PlayerWarpService playerWarpService;
    private TpaService tpaService;
    private PendingTeleportManager pendingTeleportManager;
    private RtpManager rtpManager;
    private TpaRuntimeManager tpaRuntimeManager;
    private NetworkPlayerNameCache networkPlayerNameCache;
    private PaperMessagingManager messaging;
    private StorageManager storageManager;
    private TeleportService teleportService;

    public PaperSettings settings() { return settings; }
    public void setSettings(PaperSettings settings) { this.settings = settings; }

    public MessageService messageService() { return messageService; }
    public void setMessageService(MessageService messageService) { this.messageService = messageService; }

    public PermissionService permissionService() { return permissionService; }
    public void setPermissionService(PermissionService permissionService) { this.permissionService = permissionService; }

    public AuditLogger auditLogger() { return auditLogger; }
    public void setAuditLogger(AuditLogger auditLogger) { this.auditLogger = auditLogger; }

    public DatabaseManager databaseManager() { return databaseManager; }
    public void setDatabaseManager(DatabaseManager databaseManager) { this.databaseManager = databaseManager; }

    public PlayerService playerService() { return playerService; }
    public void setPlayerService(PlayerService playerService) { this.playerService = playerService; }

    public WarpService warpService() { return warpService; }
    public void setWarpService(WarpService warpService) { this.warpService = warpService; }

    public PlayerWarpService playerWarpService() { return playerWarpService; }
    public void setPlayerWarpService(PlayerWarpService playerWarpService) { this.playerWarpService = playerWarpService; }

    public TpaService tpaService() { return tpaService; }
    public void setTpaService(TpaService tpaService) { this.tpaService = tpaService; }

    public PendingTeleportManager pendingTeleportManager() { return pendingTeleportManager; }
    public void setPendingTeleportManager(PendingTeleportManager pendingTeleportManager) { this.pendingTeleportManager = pendingTeleportManager; }

    public RtpManager rtpManager() { return rtpManager; }
    public void setRtpManager(RtpManager rtpManager) { this.rtpManager = rtpManager; }

    public TpaRuntimeManager tpaRuntimeManager() { return tpaRuntimeManager; }
    public void setTpaRuntimeManager(TpaRuntimeManager tpaRuntimeManager) { this.tpaRuntimeManager = tpaRuntimeManager; }

    public NetworkPlayerNameCache networkPlayerNameCache() { return networkPlayerNameCache; }
    public void setNetworkPlayerNameCache(NetworkPlayerNameCache networkPlayerNameCache) { this.networkPlayerNameCache = networkPlayerNameCache; }

    public PaperMessagingManager messaging() { return messaging; }
    public void setMessaging(PaperMessagingManager messaging) { this.messaging = messaging; }

    public StorageManager storageManager() { return storageManager; }
    public void setStorageManager(StorageManager storageManager) { this.storageManager = storageManager; }

    public TeleportService teleportService() { return teleportService; }
    public void setTeleportService(TeleportService teleportService) { this.teleportService = teleportService; }

    private HomeService homeService;
    public HomeService homeService() { return homeService; }
    public void setHomeService(HomeService homeService) { this.homeService = homeService; }

    private BackLocationManager backLocationManager;
    public BackLocationManager backLocationManager() { return backLocationManager; }
    public void setBackLocationManager(BackLocationManager backLocationManager) { this.backLocationManager = backLocationManager; }

    private TeleportEffectPlayer teleportEffectPlayer;
    public TeleportEffectPlayer teleportEffectPlayer() { return teleportEffectPlayer; }
    public void setTeleportEffectPlayer(TeleportEffectPlayer teleportEffectPlayer) { this.teleportEffectPlayer = teleportEffectPlayer; }

    private RandomRespawnManager randomRespawnManager;
    public RandomRespawnManager randomRespawnManager() { return randomRespawnManager; }
    public void setRandomRespawnManager(RandomRespawnManager randomRespawnManager) { this.randomRespawnManager = randomRespawnManager; }
}
