package com.telehop.common.db;

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
                  uuid    VARCHAR(36) NOT NULL,
                  slot    INT         NOT NULL,
                  server  VARCHAR(64) NOT NULL,
                  world   VARCHAR(64) NOT NULL,
                  x       DOUBLE      NOT NULL,
                  y       DOUBLE      NOT NULL,
                  z       DOUBLE      NOT NULL,
                  yaw     FLOAT       NOT NULL,
                  pitch   FLOAT       NOT NULL,
                  PRIMARY KEY (uuid, slot)
                )
                """
        );
    }

    /**
     * Migration statements for existing databases upgrading from
     * the old {@code expiry} column to {@code sent_at}.
     */
    public static List<String> migrations() {
        return List.of(
                """
                ALTER TABLE tpa_requests CHANGE COLUMN expiry sent_at BIGINT NOT NULL
                """
        );
    }
}
