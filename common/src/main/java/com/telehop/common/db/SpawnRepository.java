package com.telehop.common.db;

import com.telehop.common.model.SpawnEntry;
import com.telehop.common.model.BackendSpawns;
import com.telehop.common.model.SpawnPoint;
import com.telehop.common.model.SpawnRoute;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/** Publishes immutable backend locations with an optimistic revision check. */
public final class SpawnRepository {
    private final DataSource source;

    public SpawnRepository(DataSource source) { this.source = source; }

    public Optional<SpawnEntry> find(String id) {
        try (var conn = source.getConnection();
             var query = conn.prepareStatement("SELECT * FROM named_spawns WHERE id = ?")) {
            query.setString(1, id);
            try (var rows = query.executeQuery()) {
                return rows.next() ? Optional.of(entry(rows)) : Optional.empty();
            }
        } catch (SQLException ex) { throw new IllegalStateException("Cannot read spawn", ex); }
    }

    public List<SpawnEntry> list() {
        try (var conn = source.getConnection();
             var query = conn.prepareStatement("SELECT * FROM named_spawns ORDER BY id");
             var rows = query.executeQuery()) {
            var result = new ArrayList<SpawnEntry>();
            while (rows.next()) result.add(entry(rows));
            return result;
        } catch (SQLException ex) { throw new IllegalStateException("Cannot list spawns", ex); }
    }

    private SpawnEntry entry(ResultSet rows) throws SQLException {
        var point = new SpawnPoint(rows.getString("world"), rows.getDouble("x"), rows.getDouble("y"),
                rows.getDouble("z"), rows.getFloat("yaw"), rows.getFloat("pitch"), rows.getBoolean("restricted"));
        return new SpawnEntry(rows.getString("id"), rows.getString("server"), point, UUID.fromString(rows.getString("revision")));
    }

    /** Replace one backend's complete snapshot. A failed claim rolls back its routes and every location. */
    public void configure(BackendSpawns config, SpawnEntry expected) {
        String server = config.server();
        var points = config.destinations();
        try (var connection = source.getConnection()) {
            // The backend row serialises its writers; unrelated backends need no gap locks.
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);
            try {
                try (var lock = connection.prepareStatement("""
                        INSERT INTO spawn_backends(server,routing_enabled,hub_command,default_world,default_x,default_y,default_z,default_yaw,default_pitch)
                        VALUES(?,?,?,?,?,?,?,?,?)
                        ON DUPLICATE KEY UPDATE routing_enabled=VALUES(routing_enabled),hub_command=VALUES(hub_command),
                        default_world=VALUES(default_world),default_x=VALUES(default_x),default_y=VALUES(default_y),
                        default_z=VALUES(default_z),default_yaw=VALUES(default_yaw),default_pitch=VALUES(default_pitch)
                        """)) {
                    lock.setString(1, server);
                    lock.setBoolean(2, config.routingEnabled());
                    lock.setBoolean(3, config.hubCommand());
                    var spawn = config.defaultSpawn();
                    lock.setString(4, spawn.world());
                    lock.setDouble(5, spawn.x());
                    lock.setDouble(6, spawn.y());
                    lock.setDouble(7, spawn.z());
                    lock.setFloat(8, spawn.yaw());
                    lock.setFloat(9, spawn.pitch());
                    lock.executeUpdate();
                }
                Map<String, SpawnEntry> old = new HashMap<>();
                try (var query = connection.prepareStatement("SELECT * FROM named_spawns WHERE server=? FOR UPDATE")) {
                    query.setString(1, server);
                    try (var rows = query.executeQuery()) {
                        while (rows.next()) {
                            var entry = entry(rows);
                            old.put(entry.id(), entry);
                        }
                    }
                }
                if (expected != null && !expected.equals(old.get(expected.id()))) {
                    throw new IllegalStateException("The spawn revision has changed");
                }
                try (var remove = connection.prepareStatement("DELETE FROM named_spawns WHERE id=? AND server=?");
                     var insert = connection.prepareStatement("""
                             INSERT INTO named_spawns(id,server,world,x,y,z,yaw,pitch,restricted,revision)
                             VALUES(?,?,?,?,?,?,?,?,?,?)
                             """)) {
                    for (var previous : old.values()) {
                        if (!previous.point().equals(points.get(previous.id()))) {
                            remove.setString(1, previous.id());
                            remove.setString(2, server);
                            remove.addBatch();
                        }
                    }
                    remove.executeBatch();
                    for (var definition : points.entrySet()) {
                        var point = definition.getValue();
                        var previous = old.get(definition.getKey());
                        if (previous != null && previous.point().equals(point)) continue;
                        insert.setString(1, definition.getKey());
                        insert.setString(2, server);
                        insert.setString(3, point.world());
                        insert.setDouble(4, point.x());
                        insert.setDouble(5, point.y());
                        insert.setDouble(6, point.z());
                        insert.setFloat(7, point.yaw());
                        insert.setFloat(8, point.pitch());
                        insert.setBoolean(9, point.restricted());
                        insert.setString(10, UUID.randomUUID().toString());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                replaceRoutes(connection, server, config.routes());
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(ex.getErrorCode() == 1062 ? "Spawn name already belongs to another backend"
                    : "Cannot synchronise backend spawns", ex);
        }
    }

    private void replaceRoutes(Connection connection, String server, Map<String, SpawnRoute> routes) throws SQLException {
        try (var clear = connection.prepareStatement("DELETE FROM spawn_routes WHERE server=?");
             var insert = connection.prepareStatement("INSERT INTO spawn_routes(server,source_world,target_server,target_spawn) VALUES(?,?,?,?)")) {
            clear.setString(1, server);
            clear.executeUpdate();
            for (var route : routes.entrySet()) {
                insert.setString(1, server);
                insert.setString(2, route.getKey());
                insert.setString(3, route.getValue().server());
                insert.setString(4, route.getValue().id());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }
}
