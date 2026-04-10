package com.telehop.paper;

import co.aikar.commands.PaperCommandManager;
import com.telehop.common.db.*;
import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import com.telehop.common.model.TpaRequestRecord;
import com.telehop.common.model.TpaType;
import com.telehop.common.model.WarpRecord;
import com.telehop.common.service.*;
import com.telehop.paper.command.*;
import com.telehop.paper.config.PaperSettings;
import com.telehop.paper.listener.PaperPlayerListener;
import com.telehop.paper.messaging.PaperMessagingManager;
import com.telehop.paper.service.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class NetworkPaperPlugin extends JavaPlugin {
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
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = PaperSettings.from(getConfig());
        initLanguageFiles();
        messageService = buildMessageService();
        permissionService = new PermissionService();
        auditLogger = new AuditLogger(this, settings.auditEnabled());
        pendingTeleportManager = new PendingTeleportManager();
        rtpManager = new RtpManager(this);
        tpaRuntimeManager = new TpaRuntimeManager();
        networkPlayerNameCache = new NetworkPlayerNameCache();

        try {
            databaseManager = new DatabaseManager(settings.databaseConfig());
            databaseManager.initSchema();
        } catch (Exception e) {
            getLogger().severe("Failed to initialize database: " + e.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        playerService = new PlayerService(databaseManager, new PlayerRepository(databaseManager.dataSource()), new PlayerServerCache());
        warpService = new WarpService(databaseManager, new WarpRepository(databaseManager.dataSource()), new WarpCache());
        playerWarpService = new PlayerWarpService(databaseManager, new PlayerWarpRepository(databaseManager.dataSource()));
        tpaService = new TpaService(databaseManager, new TpaRepository(databaseManager.dataSource()), new TpaRequestCache());
        warpService.refreshCache();

        messaging = new PaperMessagingManager(this, settings.dedupeWindowMs(), settings.requestTimeoutMs());
        messaging.register();
        messaging.setHandler(this::handleIncomingPacket);

        getServer().getPluginManager().registerEvents(new PaperPlayerListener(this), this);
        registerCommands();
        startTpaExpiryTask();
    }

    @Override
    public void onDisable() {
        if (messaging != null) messaging.shutdown();
        if (databaseManager != null) databaseManager.shutdown();
    }

    private void registerCommands() {
        PaperCommandManager manager = new PaperCommandManager(this);
        manager.enableUnstableAPI("brigadier");
        manager.enableUnstableAPI("help");
        try {
            String consoleMini = messageService.rawString("console-not-allowed");
            net.kyori.adventure.text.Component comp = miniMessage.deserialize(consoleMini);
            String legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().serialize(comp);
            manager.getLocales().addMessage(java.util.Locale.ENGLISH,
                    co.aikar.commands.MessageKeys.NOT_ALLOWED_ON_CONSOLE, legacy);
        } catch (Exception e) {
            getLogger().warning("Could not set ACF console message: " + e.getMessage());
        }
        manager.getCommandCompletions().registerCompletion("networkplayers", c -> networkPlayerNameCache.list());
        manager.getCommandCompletions().registerCompletion("warps", c -> listWarpNames());
        manager.getCommandCompletions().registerCompletion("playerwarps", c -> {
            if (c.getPlayer() != null) {
                return playerWarpService.listByOwner(c.getPlayer().getUniqueId().toString())
                        .join().stream().map(com.telehop.common.model.PlayerWarpRecord::name).toList();
            }
            return List.of();
        });
        manager.registerCommand(new SpawnCommand(this));
        manager.registerCommand(new WarpCommand(this));
        manager.registerCommand(new SetWarpCommand(this));
        manager.registerCommand(new DelWarpCommand(this));
        manager.registerCommand(new WarpsCommand(this));
        manager.registerCommand(new TpaCommand(this));
        manager.registerCommand(new TpaHereCommand(this));
        manager.registerCommand(new TpaAcceptCommand(this));
        manager.registerCommand(new TpaDenyCommand(this));
        manager.registerCommand(new TpaCancelCommand(this));
        manager.registerCommand(new AdminTeleportCommand(this));
        manager.registerCommand(new TpHereAdminCommand(this));
        manager.registerCommand(new RtpCommand(this));
        manager.registerCommand(new PlayerWarpCommand(this));
        manager.registerCommand(new TeleHopCommand(this));
        manager.registerCommand(new ListWarpsCommand(this));
        manager.registerCommand(new ForceDelWarpCommand(this));
    }

    private void startTpaExpiryTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () ->
                tpaService.expiredNow().thenAccept(expired -> {
                    for (var request : expired) {
                        tpaService.delete(request.senderUuid(), request.targetUuid());
                        tpaRuntimeManager.removeIncoming(request.targetUuid());
                    }
                    if (!expired.isEmpty()) {
                        Bukkit.getScheduler().runTask(this, () -> {
                            for (var request : expired) {
                                Player sender = Bukkit.getPlayer(request.senderUuid());
                                if (sender != null) sender.sendMessage(msg("request-expired"));
                                Player target = Bukkit.getPlayer(request.targetUuid());
                                if (target != null) target.sendMessage(msg("request-expired"));
                            }
                        });
                    }
                }), 20L, 20L * 5L);

        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            NetworkPacket packet = NetworkPacket.request(PacketType.PLAYER_LIST_REQUEST, settings.serverName(), "velocity");
            messaging.send(packet);
        }, 40L, 20L * 5L);
    }

    private static final List<String> LANGUAGE_CODES = List.of("en", "nl", "de", "es", "zh", "pl");

    private void initLanguageFiles() {
        File langDir = new File(getDataFolder(), "languages");
        langDir.mkdirs();
        for (String code : LANGUAGE_CODES) {
            File target = new File(langDir, code + ".yml");
            if (!target.exists()) {
                saveResource("languages/" + code + ".yml", false);
            }
        }
    }

    private MessageService buildMessageService() {
        String lang = settings.language();
        File langDir = new File(getDataFolder(), "languages");
        File selected = new File(langDir, lang + ".yml");
        if (!selected.exists()) {
            getLogger().warning("Language '" + lang + "' not found, falling back to English.");
            selected = new File(langDir, "en.yml");
        }
        FileConfiguration langFile = YamlConfiguration.loadConfiguration(selected);
        FileConfiguration fallback = YamlConfiguration.loadConfiguration(new File(langDir, "en.yml"));
        return new MessageService(langFile, fallback);
    }

    private void handleIncomingPacket(NetworkPacket packet) {
        if (packet.getType() == PacketType.EXECUTE_POST_JOIN_TELEPORT) {
            UUID playerId = UUID.fromString(packet.get("uuid"));
            String worldName = packet.get("world");
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                Location location = new Location(
                        world,
                        Double.parseDouble(packet.get("x")),
                        Double.parseDouble(packet.get("y")),
                        Double.parseDouble(packet.get("z")),
                        Float.parseFloat(packet.getOrDefault("yaw", "0")),
                        Float.parseFloat(packet.getOrDefault("pitch", "0"))
                );
                pendingTeleportManager.setPending(playerId, location);
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    executePendingTeleport(player);
                }
            }
        } else if (packet.getType() == PacketType.WARP_TELEPORT) {
            String warpName = packet.get("warp");
            Player player = Bukkit.getPlayer(UUID.fromString(packet.get("uuid")));
            if (player != null) {
                warpService.find(warpName).thenAccept(warp -> warp.ifPresent(w -> teleportToWarp(player, w)));
            }
        } else if (packet.getType() == PacketType.SPAWN_REQUEST) {
            Player player = Bukkit.getPlayer(UUID.fromString(packet.get("uuid")));
            if (player != null) {
                teleportToSpawn(player);
            }
        } else if (packet.getType() == PacketType.TPA_CREATE) {
            UUID senderUuid = UUID.fromString(packet.get("senderUuid"));
            UUID targetUuid = UUID.fromString(packet.get("targetUuid"));
            TpaType type = TpaType.valueOf(packet.get("type"));
            java.time.Instant expiry = java.time.Instant.ofEpochMilli(Long.parseLong(packet.get("expiry")));
            TpaRequestRecord request = new TpaRequestRecord(senderUuid, targetUuid, type, expiry);
            tpaRuntimeManager.setIncoming(request);
            tpaService.upsert(request);
            if (!packet.getOriginServer().equalsIgnoreCase(settings.serverName())) {
                Player target = Bukkit.getPlayer(targetUuid);
                if (target != null) {
                    target.sendMessage(msg(type == TpaType.TPA ? "request-received" : "request-received-here",
                            Map.of("sender", packet.get("senderName"))));
                    target.sendMessage(messageService.raw("request-actions"));
                }
            }
        } else if (packet.getType() == PacketType.TPA_DENY) {
            Player sender = Bukkit.getPlayer(UUID.fromString(packet.get("senderUuid")));
            if (sender != null) {
                sender.sendMessage(msg("request-denied"));
            }
        } else if (packet.getType() == PacketType.TPA_CANCEL) {
            Player target = Bukkit.getPlayer(UUID.fromString(packet.get("targetUuid")));
            if (target != null) {
                target.sendMessage(msg("request-cancelled"));
            }
        } else if (packet.getType() == PacketType.RTP_REQUEST) {
            Player player = Bukkit.getPlayer(UUID.fromString(packet.get("uuid")));
            if (player != null) {
                executeLocalRtp(player, packet.getOrDefault("region", "default"), packet.getOrDefault("dimension", "overworld"));
            }
        } else if (packet.getType() == PacketType.TELEPORT_TO_PLAYER) {
            Player actor = Bukkit.getPlayer(UUID.fromString(packet.get("actorUuid")));
            Player target = Bukkit.getPlayer(UUID.fromString(packet.get("targetUuid")));
            if (actor != null && target != null) {
                actor.teleportAsync(target.getLocation());
            }
        } else if (packet.getType() == PacketType.PWARP_TELEPORT) {
            Player player = Bukkit.getPlayer(UUID.fromString(packet.get("uuid")));
            if (player != null) {
                String ownerUuid = packet.get("pwarpOwner");
                String pwarpName = packet.get("pwarpName");
                playerWarpService.find(ownerUuid, pwarpName).thenAccept(opt -> opt.ifPresent(warp -> {
                    World world = Bukkit.getWorld(warp.world());
                    if (world != null) {
                        Location loc = new Location(world, warp.x(), warp.y(), warp.z(), warp.yaw(), warp.pitch());
                        Bukkit.getScheduler().runTask(this, () -> player.teleportAsync(loc));
                    }
                }));
            }
        } else if (packet.getType() == PacketType.PLAYER_LIST_RESPONSE) {
            String csv = packet.getOrDefault("names", "");
            List<String> names = csv.isBlank() ? List.of() : List.of(csv.split(","));
            networkPlayerNameCache.replace(names.stream().map(String::trim).filter(s -> !s.isBlank()).toList());
        }
    }

    public void executeLocalRtp(Player player, String region, String dimension) {
        String worldName = resolveRtpWorldName(dimension);
        int maxRadius = getConfig().getInt("rtp.max-radius", 25000);
        int radius = Math.min(getConfig().getInt("rtp.regions." + region + ".radius", 25000), maxRadius);
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage(msg("rtp-failed"));
            auditLogger.log("rtp-failed-world player=" + player.getName() + " region=" + region + " dimension=" + dimension + " worldName=" + worldName);
            return;
        }
        auditLogger.log("rtp-start player=" + player.getName() + " region=" + region + " dimension=" + dimension + " world=" + world.getName() + " radius=" + radius);
        player.sendMessage(msg("rtp-searching"));
        rtpManager.findSafeLocation(world, radius).thenAccept(location -> {
            Bukkit.getScheduler().runTask(this, () -> {
                if (location == null) {
                    player.sendMessage(msg("rtp-failed"));
                    auditLogger.log("rtp-failed-safe-spot player=" + player.getName() + " world=" + world.getName());
                    return;
                }
                player.sendMessage(msg("rtp-teleporting"));
                auditLogger.log("rtp-teleport player=" + player.getName() + " world=" + world.getName() + " x=" + location.getBlockX() + " y=" + location.getBlockY() + " z=" + location.getBlockZ());
                rtpManager.teleport(player, location);
            });
        });
    }

    private String resolveRtpWorldName(String dimension) {
        String key = "rtp.dimensions." + dimension;
        String configured = getConfig().getString(key);
        if (configured != null && Bukkit.getWorld(configured) != null) {
            return configured;
        }
        return switch (dimension.toLowerCase()) {
            case "nether" -> firstWorldByEnvironment(World.Environment.NETHER).orElse("world_nether");
            case "end" -> firstWorldByEnvironment(World.Environment.THE_END).orElse("world_the_end");
            default -> firstWorldByEnvironment(World.Environment.NORMAL).orElse("world");
        };
    }

    private Optional<String> firstWorldByEnvironment(World.Environment environment) {
        return Bukkit.getWorlds().stream()
                .filter(w -> w.getEnvironment() == environment)
                .map(World::getName)
                .findFirst();
    }

    public void executePendingTeleport(Player player) {
        Location location = pendingTeleportManager.take(player.getUniqueId());
        if (location != null) {
            player.teleportAsync(location);
        }
    }

    public void clearPendingTeleport(UUID playerUuid) {
        pendingTeleportManager.take(playerUuid);
    }

    public Location getSpawnLocation() {
        String worldName = getConfig().getString("spawn.location.world",
                getConfig().getString("spawn.world", "world"));
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
        if (world == null) return null;
        return new Location(
                world,
                getConfig().getDouble("spawn.location.x", getConfig().getDouble("spawn.x", 0.5)),
                getConfig().getDouble("spawn.location.y", getConfig().getDouble("spawn.y", 100.0)),
                getConfig().getDouble("spawn.location.z", getConfig().getDouble("spawn.z", 0.5)),
                (float) getConfig().getDouble("spawn.location.yaw", getConfig().getDouble("spawn.yaw", 0.0)),
                (float) getConfig().getDouble("spawn.location.pitch", getConfig().getDouble("spawn.pitch", 0.0))
        );
    }

    public void teleportToWarp(Player player, WarpRecord warp) {
        World world = Bukkit.getWorld(warp.world());
        if (world == null) return;
        Location target = new Location(world, warp.x(), warp.y(), warp.z(), warp.yaw(), warp.pitch());
        player.teleportAsync(target);
    }

    public void teleportToSpawn(Player player) {
        String worldName = getConfig().getString("spawn.location.world",
                getConfig().getString("spawn.world", "world"));
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;
        Location location = new Location(
                world,
                getConfig().getDouble("spawn.location.x", getConfig().getDouble("spawn.x", 0.5)),
                getConfig().getDouble("spawn.location.y", getConfig().getDouble("spawn.y", 100.0)),
                getConfig().getDouble("spawn.location.z", getConfig().getDouble("spawn.z", 0.5)),
                (float) getConfig().getDouble("spawn.location.yaw", getConfig().getDouble("spawn.yaw", 0.0)),
                (float) getConfig().getDouble("spawn.location.pitch", getConfig().getDouble("spawn.pitch", 0.0))
        );
        player.teleportAsync(location);
    }

    public Component msg(String key) {
        return messageService.format(key);
    }

    public Component msg(String key, Map<String, String> replacements) {
        return messageService.format(key, replacements);
    }

    public Component mm(String mini) {
        return miniMessage.deserialize(mini);
    }

    public boolean isFeatureEnabled(String feature) {
        return settings.isFeatureEnabled(feature);
    }

    public void reload() {
        reloadConfig();
        settings = PaperSettings.from(getConfig());
        initLanguageFiles();
        messageService = buildMessageService();
        warpService.refreshCache();
        auditLogger = new AuditLogger(this, settings.auditEnabled());
        getLogger().info("TeleHop configuration reloaded (language: " + settings.language() + ").");
    }

    public PaperSettings settings() {
        return settings;
    }

    public MessageService messageService() {
        return messageService;
    }

    public PermissionService permissionService() {
        return permissionService;
    }

    public AuditLogger auditLogger() {
        return auditLogger;
    }

    public PlayerService playerService() {
        return playerService;
    }

    public WarpService warpService() {
        return warpService;
    }

    public PlayerWarpService playerWarpService() {
        return playerWarpService;
    }

    public TpaService tpaService() {
        return tpaService;
    }

    public RtpManager rtpManager() {
        return rtpManager;
    }

    public TpaRuntimeManager tpaRuntimeManager() {
        return tpaRuntimeManager;
    }

    public PaperMessagingManager messaging() {
        return messaging;
    }

    /**
     * Resolves a region label to a Velocity server name via the dynamic servers map.
     */
    public Optional<String> regionServer(String region) {
        String mapped = settings.servers().get(region.toLowerCase());
        return mapped != null ? Optional.of(mapped) : Optional.empty();
    }

    public List<String> listWarpNames() {
        return warpService.listCached().stream().map(WarpRecord::name).toList();
    }

    public List<String> listNetworkPlayers() {
        return networkPlayerNameCache.list();
    }
}
