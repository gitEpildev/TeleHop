import com.telehop.common.db.RedisConfig;
import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import com.telehop.velocity.messaging.RedisCrossProxyBridge;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Exercises the bridge from the shaded Velocity JAR against a disposable Redis process. */
class RedisBridgeCheck {
    static final int PORT = 16379;
    static final String CHANNEL = "telehop-ci:cross-proxy";

    static Process startRedis() throws Exception {
        return new ProcessBuilder("redis-server", "--bind", "127.0.0.1", "--port", String.valueOf(PORT),
                "--save", "", "--appendonly", "no").inheritIO().start();
    }

    static void stopRedis(Process redis) throws Exception {
        redis.destroy();
        if (!redis.waitFor(10, TimeUnit.SECONDS)) {
            redis.destroyForcibly().waitFor();
            throw new AssertionError("Redis did not stop cleanly");
        }
    }

    static boolean subscribed() {
        try {
            var cli = new ProcessBuilder("redis-cli", "-p", String.valueOf(PORT), "--raw",
                    "PUBSUB", "NUMSUB", CHANNEL).start();
            return cli.waitFor(3, TimeUnit.SECONDS) && cli.exitValue() == 0
                    && cli.inputReader().lines().toList().equals(java.util.List.of(CHANNEL, "2"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static void await(String condition, BooleanSupplier ready) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!ready.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError("Timed out waiting for " + condition);
            Thread.sleep(100);
        }
    }

    static RedisCrossProxyBridge bridge(String proxy) {
        return new RedisCrossProxyBridge(new RedisConfig(true, "127.0.0.1", PORT, "", "telehop-ci",
                proxy, false, true, ""), LoggerFactory.getLogger(proxy));
    }

    public static void main(String[] args) throws Exception {
        var redis = startRedis();
        var a = bridge("proxy-a");
        var b = bridge("proxy-b");
        var remote = new LinkedBlockingQueue<NetworkPacket>();
        var echo = new LinkedBlockingQueue<NetworkPacket>();
        try {
            a.setPacketHandler(echo::add);
            b.setPacketHandler(remote::add);
            a.start();
            b.start();
            await("two Redis subscriptions", RedisBridgeCheck::subscribed);
            var id = UUID.randomUUID();
            a.broadcastPlayerUpdate(id, "Alex", "survival", "JOIN");
            await("remote join", () -> "survival".equals(b.globalPlayerMap().get(id))
                    && id.equals(b.globalPlayerNameMap().get("alex")));
            if (a.globalPlayerMap().containsKey(id)) throw new AssertionError("Origin consumed its own join");

            stopRedis(redis);
            redis = startRedis();
            await("both bridges to reconnect", RedisBridgeCheck::subscribed);
            a.broadcastPlayerUpdate(id, "Alex", "", "LEAVE");
            await("remote leave", () -> !b.globalPlayerMap().containsKey(id)
                    && !b.globalPlayerNameMap().containsKey("alex"));

            var samples = new ArrayList<Long>();
            for (int i = 0; i < 30; i++) {
                var request = NetworkPacket.request(PacketType.ADMIN_TP_REQUEST, "lobby", "survival")
                        .put("targetName", "Alex");
                long start = System.nanoTime();
                a.forwardPacket(request);
                var received = remote.poll(5, TimeUnit.SECONDS);
                if (received == null || !request.getRequestId().equals(received.getRequestId())
                        || !"Alex".equals(received.get("targetName"))) throw new AssertionError("Packet delivery failed");
                samples.add(TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - start));
            }
            if (!echo.isEmpty()) throw new AssertionError("Forwarded packets echoed to their sender");
            Collections.sort(samples);
            System.out.printf("Redis forwarding: 30/30 packets received, median %d us, p95 %d us%n",
                    samples.get(14), samples.get(28));
        } finally {
            a.shutdown();
            b.shutdown();
            stopRedis(redis);
        }
    }
}
