import com.telehop.common.db.DatabaseConfig;
import com.telehop.common.db.DatabaseManager;
import com.telehop.common.db.SpawnRepository;
import com.telehop.common.model.SpawnPoint;
import com.telehop.common.model.BackendSpawns;
import com.telehop.common.model.SpawnRoute;
import com.telehop.common.service.SpawnDirectory;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;

/** Verifies atomic replacement, ownership and stable revisions against a real disposable database. */
class SpawnDirectoryCheck {
    public static void main(String[] args) throws Exception {
        var db = new DatabaseManager(new DatabaseConfig("127.0.0.1", Integer.parseInt(System.getenv("MYSQL_PORT")),
                "telehop_ci", "telehop", System.getenv("MYSQL_PWD"), 3));
        try {
            db.initSchema();
            var dir = new SpawnDirectory(db, new SpawnRepository(db.dataSource()));
            String id = "garden-" + UUID.randomUUID().toString().substring(0, 8);
            var point = new SpawnPoint("custom:garden", 12.5, 70, -9.75, 28, -14, true);
            var points = Map.of(id, point);
            var routes = Map.of("training", new SpawnRoute("example", id));
            var first = dir.configure(new BackendSpawns("probe-a", point, true, false, points, routes), null).handle((v, e) -> e == null);
            var second = dir.configure(new BackendSpawns("probe-b", point, true, false, points, routes), null).handle((v, e) -> e == null);
            if (first.join() == second.join()) throw new AssertionError("Exactly one backend must win");
            var owner = dir.find(id).join().orElseThrow();
            if (!owner.point().equals(point)) throw new AssertionError("Lost raw location fields");
            dir.configure(new BackendSpawns(owner.server(), point, true, false, points, routes), null).join();
            if (!owner.equals(dir.find(id).join().orElseThrow())) throw new AssertionError("Unmodified revision changed");
            var next = new SpawnPoint("custom:arena", 12.5, 70, -9.75, 28, -14, false);
            dir.configure(new BackendSpawns(owner.server(), point, false, true, Map.of(id, next), Map.of()), owner).join();
            try {
                dir.configure(new BackendSpawns(owner.server(), point, false, true, Map.of(), Map.of()), owner).join();
                throw new AssertionError("Accepted a stale deletion");
            } catch (CompletionException expected) {
                if (!dir.find(id).join().orElseThrow().point().equals(next)) throw new AssertionError("Lost committed edit");
            }
            dir.configure(new BackendSpawns(owner.server(), point, false, true, Map.of(), Map.of()), null).join();
            if (dir.find(id).join().isPresent()) throw new AssertionError("Removed YAML name still published");
            System.out.println("Concurrent ownership, raw fields, rollback and revision-bound deletion passed");
        } finally { db.shutdown(); }
    }
}
