package com.telehop.common.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Provides the DDL statements that create TeleHop's database tables
 * and any migration statements for upgrading existing schemas.
 * Executed once during {@link DatabaseManager#initSchema()}.
 */
public final class SqlSchema {
    private SqlSchema() {
    }

    public static List<String> statements() {
        return List.of(
                """
                CREATE TABLE IF NOT EXISTS spawn_backends (
                  server VARCHAR(64) PRIMARY KEY,
                  routing_enabled BOOLEAN NOT NULL,
                  hub_command BOOLEAN NOT NULL,
                  default_world VARCHAR(255) NOT NULL,
                  default_x DOUBLE NOT NULL, default_y DOUBLE NOT NULL, default_z DOUBLE NOT NULL,
                  default_yaw FLOAT NOT NULL, default_pitch FLOAT NOT NULL
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS spawn_routes (
                  server VARCHAR(64) NOT NULL,
                  source_world VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
                  target_server VARCHAR(64) NOT NULL,
                  target_spawn VARCHAR(32),
                  PRIMARY KEY (server, source_world)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS named_spawns (
                  id VARCHAR(32) PRIMARY KEY,
                  server VARCHAR(64) NOT NULL,
                  world VARCHAR(255) NOT NULL,
                  x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL,
                  yaw FLOAT NOT NULL, pitch FLOAT NOT NULL,
                  restricted BOOLEAN NOT NULL,
                  revision CHAR(36) NOT NULL,
                  INDEX spawn_owner (server)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS players (
                  uuid VARCHAR(36) PRIMARY KEY,
                  current_server VARCHAR(64) NOT NULL,
                  last_seen BIGINT NOT NULL
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS warps (
                  name VARCHAR(64) PRIMARY KEY,
                  server VARCHAR(64) NOT NULL,
                  world VARCHAR(64) NOT NULL,
                  x DOUBLE NOT NULL,
                  y DOUBLE NOT NULL,
                  z DOUBLE NOT NULL,
                  yaw FLOAT NOT NULL,
                  pitch FLOAT NOT NULL
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS player_warps (
                  owner_uuid VARCHAR(36) NOT NULL,
                  name       VARCHAR(64) NOT NULL,
                  server     VARCHAR(64) NOT NULL,
                  world      VARCHAR(64) NOT NULL,
                  x          DOUBLE NOT NULL,
                  y          DOUBLE NOT NULL,
                  z          DOUBLE NOT NULL,
                  yaw        FLOAT NOT NULL,
                  pitch      FLOAT NOT NULL,
                  is_public  TINYINT(1) NOT NULL DEFAULT 0,
                  PRIMARY KEY (owner_uuid, name)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS tpa_requests (
                  sender_uuid VARCHAR(36) NOT NULL,
                  target_uuid VARCHAR(36) NOT NULL,
                  type VARCHAR(16) NOT NULL,
                  sent_at BIGINT NOT NULL,
                  PRIMARY KEY (sender_uuid, target_uuid)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS homes (
                  uuid    VARCHAR(36)  NOT NULL,
                  name    VARCHAR(32)  NOT NULL,
                  server  VARCHAR(64)  NOT NULL,
                  world   VARCHAR(64)  NOT NULL,
                  x       DOUBLE       NOT NULL,
                  y       DOUBLE       NOT NULL,
                  z       DOUBLE       NOT NULL,
                  yaw     FLOAT        NOT NULL,
                  pitch   FLOAT        NOT NULL,
                  PRIMARY KEY (uuid, name)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS last_locations (
                  uuid   VARCHAR(36) PRIMARY KEY,
                  server VARCHAR(64) NOT NULL,
                  world  VARCHAR(64) NOT NULL,
                  x      DOUBLE      NOT NULL,
                  y      DOUBLE      NOT NULL,
                  z      DOUBLE      NOT NULL,
                  yaw    FLOAT       NOT NULL,
                  pitch  FLOAT       NOT NULL
                )
                """
        );
    }

    /** Serialises concurrent backend and proxy starts against the same MySQL database. */
    static void initialise(Connection connection) throws SQLException {
        String lock = "CONCAT('TeleHop:', LEFT(SHA2(DATABASE(), 256), 56))";
        try (var sql = connection.createStatement()) {
            try (var acquired = sql.executeQuery("SELECT GET_LOCK(" + lock + ", 60)")) {
                if (!acquired.next() || acquired.getInt(1) != 1) {
                    throw new SQLException("Timed out waiting for another TeleHop instance to finish the schema upgrade");
                }
            }
            try {
                for (String ddl : statements()) {
                    sql.execute(ddl);
                }
                if (hasColumn(connection, "tpa_requests", "expiry")) {
                    sql.execute("ALTER TABLE tpa_requests CHANGE COLUMN expiry sent_at BIGINT NOT NULL");
                }
                if (hasColumn(connection, "homes", "slot")) {
                    if (!hasColumn(connection, "homes", "name")) {
                        sql.execute("ALTER TABLE homes ADD COLUMN name VARCHAR(32) NOT NULL DEFAULT ''");
                    }
                    sql.executeUpdate("UPDATE homes SET name = CONCAT('Home ', slot) WHERE name = ''");
                    // A conflicting name must leave the old slot and primary key available for recovery.
                    sql.execute("ALTER TABLE homes DROP PRIMARY KEY, ADD PRIMARY KEY (uuid, name), DROP COLUMN slot");
                }
            } finally {
                sql.execute("SELECT RELEASE_LOCK(" + lock + ")");
            }
        }
    }

    private static boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        try (var query = connection.prepareStatement("""
                SELECT 1 FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
                """)) {
            query.setString(1, table);
            query.setString(2, column);
            try (var result = query.executeQuery()) {
                return result.next();
            }
        }
    }
}
