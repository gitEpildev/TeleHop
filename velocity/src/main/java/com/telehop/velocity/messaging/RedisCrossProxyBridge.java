package com.telehop.velocity.messaging;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.telehop.common.db.RedisConfig;
import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import org.slf4j.Logger;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Redis-based bridge for cross-proxy communication.
 * When multiple Velocity proxies share the same backends, this bridge
 * allows them to forward packets, share player locations, and aggregate
 * player lists via Redis pub/sub.
 */
public final class RedisCrossProxyBridge {

    private static final Gson GSON = new GsonBuilder().create();

    private final RedisConfig config;
    private final Logger logger;
    private final JedisPool pool;
    private final String channel;
    private final String proxyId;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private final Map<UUID, String> globalPlayerMap = new ConcurrentHashMap<>();
    private final Map<String, UUID> globalPlayerNameMap = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<String>> pendingResponses = new ConcurrentHashMap<>();

    private volatile Consumer<NetworkPacket> packetHandler;
    private volatile boolean running;
    private Thread subscriberThread;

    public RedisCrossProxyBridge(RedisConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
        this.channel = config.crossProxyChannel();
        this.proxyId = config.proxyId();

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(4);
        poolConfig.setMaxIdle(2);

        DefaultJedisClientConfig.Builder clientConfig = DefaultJedisClientConfig.builder()
                .timeoutMillis(5000);
        if (config.password() != null && !config.password().isBlank()) {
            clientConfig.password(config.password());
        }
        if (config.ssl()) {
            RedisSslContext sslContext = RedisSslContext.create(config);
            clientConfig.ssl(true)
                    .sslSocketFactory(sslContext.socketFactory());
            if (sslContext.hostnameVerifier() != null) {
                clientConfig.hostnameVerifier(sslContext.hostnameVerifier());
            }
            logger.info("Redis TLS enabled (verify={}, ca-cert={})",
                    config.sslVerify(),
                    config.sslCaCert().isBlank() ? "system trust store" : config.sslCaCert());
        }

        this.pool = new JedisPool(poolConfig, new HostAndPort(config.host(), config.port()), clientConfig.build());
    }

    public void setPacketHandler(Consumer<NetworkPacket> handler) {
        this.packetHandler = handler;
    }

    public Map<UUID, String> globalPlayerMap() {
        return globalPlayerMap;
    }

    public Map<String, UUID> globalPlayerNameMap() {
        return globalPlayerNameMap;
    }

    public void start() {
        running = true;
        subscriberThread = new Thread(this::subscribe, "TeleHop-Redis-Sub");
        subscriberThread.setDaemon(true);
        subscriberThread.start();

        scheduler.scheduleAtFixedRate(this::cleanStaleResponses, 30, 30, TimeUnit.SECONDS);

        logger.info("RedisCrossProxyBridge started (proxyId={}, channel={})", proxyId, channel);
    }

    public void shutdown() {
        running = false;
        if (subscriberThread != null) {
            subscriberThread.interrupt();
        }
        scheduler.shutdownNow();
        pool.close();
        logger.info("RedisCrossProxyBridge shut down.");
    }

    /**
     * Forwards a packet to be handled by whichever proxy owns the target player.
     */
    public void forwardPacket(NetworkPacket packet) {
        JsonObject msg = new JsonObject();
        msg.addProperty("proxyId", proxyId);
        msg.addProperty("action", "FORWARD_PACKET");
        msg.addProperty("correlationId", UUID.randomUUID().toString());
        msg.addProperty("packet", GSON.toJson(packet));
        publish(msg);
    }

    /**
     * Broadcasts a player location update to all other proxies.
     */
    public void broadcastPlayerUpdate(UUID playerUuid, String playerName, String server, String action) {
        JsonObject msg = new JsonObject();
        msg.addProperty("proxyId", proxyId);
        msg.addProperty("action", "PLAYER_UPDATE");
        msg.addProperty("uuid", playerUuid.toString());
        msg.addProperty("name", playerName != null ? playerName : "");
        msg.addProperty("server", server != null ? server : "");
        msg.addProperty("updateAction", action);
        publish(msg);
    }

    /**
     * Requests the player list from all other proxies and returns a future
     * containing the comma-separated remote player names.
     */
    public CompletableFuture<String> requestRemotePlayerList(long timeoutMs) {
        String correlationId = UUID.randomUUID().toString();
        CompletableFuture<String> future = new CompletableFuture<>();
        pendingResponses.put(correlationId, future);

        JsonObject msg = new JsonObject();
        msg.addProperty("proxyId", proxyId);
        msg.addProperty("action", "PLAYER_LIST_REQUEST");
        msg.addProperty("correlationId", correlationId);
        publish(msg);

        scheduler.schedule(() -> {
            CompletableFuture<String> pending = pendingResponses.remove(correlationId);
            if (pending != null && !pending.isDone()) {
                pending.complete("");
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);

        return future;
    }

    // ── private ─────────────────────────────────────────────────────

    private void publish(JsonObject msg) {
        scheduler.execute(() -> {
            try (Jedis jedis = pool.getResource()) {
                jedis.publish(channel, msg.toString());
            } catch (Exception e) {
                logger.warn("Failed to publish Redis message: {}", e.getMessage());
            }
        });
    }

    private void subscribe() {
        while (running) {
            try (Jedis jedis = pool.getResource()) {
                jedis.subscribe(new JedisPubSub() {
                    @Override
                    public void onMessage(String ch, String message) {
                        if (!running) return;
                        try {
                            handleMessage(message);
                        } catch (Exception e) {
                            logger.warn("Error handling Redis message: {}", e.getMessage());
                        }
                    }
                }, channel);
            } catch (Exception e) {
                if (!running) return;
                logger.warn("Redis subscriber disconnected, reconnecting in 3s: {}", e.getMessage());
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void handleMessage(String raw) {
        JsonObject msg = JsonParser.parseString(raw).getAsJsonObject();
        String sourceProxy = msg.get("proxyId").getAsString();

        if (sourceProxy.equals(proxyId)) return;

        String action = msg.get("action").getAsString();

        switch (action) {
            case "FORWARD_PACKET" -> {
                String packetJson = msg.get("packet").getAsString();
                NetworkPacket packet = GSON.fromJson(packetJson, NetworkPacket.class);
                Consumer<NetworkPacket> handler = this.packetHandler;
                if (handler != null) {
                    handler.accept(packet);
                }
            }
            case "PLAYER_UPDATE" -> {
                String uuidStr = msg.get("uuid").getAsString();
                String server = msg.get("server").getAsString();
                String updateAction = msg.get("updateAction").getAsString();
                String name = msg.has("name") ? msg.get("name").getAsString() : "";
                UUID uuid = UUID.fromString(uuidStr);

                if ("LEAVE".equals(updateAction) || server.isBlank()) {
                    globalPlayerMap.remove(uuid);
                    if (!name.isBlank()) globalPlayerNameMap.remove(name.toLowerCase());
                } else {
                    globalPlayerMap.put(uuid, server);
                    if (!name.isBlank()) globalPlayerNameMap.put(name.toLowerCase(), uuid);
                }
            }
            case "PLAYER_LIST_REQUEST" -> {
                String correlationId = msg.get("correlationId").getAsString();
                Consumer<NetworkPacket> handler = this.packetHandler;
                if (handler != null) {
                    NetworkPacket listPacket = NetworkPacket.request(
                            PacketType.CROSS_PROXY_PLAYER_LIST, proxyId, sourceProxy);
                    listPacket.put("correlationId", correlationId);
                    handler.accept(listPacket);
                }
            }
            case "PLAYER_LIST_RESPONSE" -> {
                String correlationId = msg.get("correlationId").getAsString();
                String names = msg.has("names") ? msg.get("names").getAsString() : "";
                CompletableFuture<String> future = pendingResponses.remove(correlationId);
                if (future != null) {
                    future.complete(names);
                }
            }
            default -> logger.debug("Unknown cross-proxy action: {}", action);
        }
    }

    /**
     * Publishes a player list response back through Redis.
     */
    public void sendPlayerListResponse(String correlationId, String names) {
        JsonObject msg = new JsonObject();
        msg.addProperty("proxyId", proxyId);
        msg.addProperty("action", "PLAYER_LIST_RESPONSE");
        msg.addProperty("correlationId", correlationId);
        msg.addProperty("names", names);
        publish(msg);
    }

    private void cleanStaleResponses() {
        pendingResponses.entrySet().removeIf(e -> e.getValue().isDone());
    }
}
