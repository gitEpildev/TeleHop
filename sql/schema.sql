-- TeleHop database schema
-- Tables are auto-created on startup by SqlSchema.java.
-- This file is for manual setup or reference only.

CREATE TABLE IF NOT EXISTS players (
  uuid           VARCHAR(36) PRIMARY KEY,
  current_server VARCHAR(64) NOT NULL,
  last_seen      BIGINT      NOT NULL
);

CREATE TABLE IF NOT EXISTS warps (
  name   VARCHAR(64) PRIMARY KEY,
  server VARCHAR(64) NOT NULL,
  world  VARCHAR(64) NOT NULL,
  x      DOUBLE      NOT NULL,
  y      DOUBLE      NOT NULL,
  z      DOUBLE      NOT NULL,
  yaw    FLOAT       NOT NULL,
  pitch  FLOAT       NOT NULL
);

CREATE TABLE IF NOT EXISTS player_warps (
  owner_uuid VARCHAR(36) NOT NULL,
  name       VARCHAR(64) NOT NULL,
  server     VARCHAR(64) NOT NULL,
  world      VARCHAR(64) NOT NULL,
  x          DOUBLE      NOT NULL,
  y          DOUBLE      NOT NULL,
  z          DOUBLE      NOT NULL,
  yaw        FLOAT       NOT NULL,
  pitch      FLOAT       NOT NULL,
  is_public  TINYINT(1)  NOT NULL DEFAULT 0,
  PRIMARY KEY (owner_uuid, name)
);

CREATE TABLE IF NOT EXISTS tpa_requests (
  sender_uuid VARCHAR(36) NOT NULL,
  target_uuid VARCHAR(36) NOT NULL,
  type        VARCHAR(16) NOT NULL,
  sent_at     BIGINT      NOT NULL,
  PRIMARY KEY (sender_uuid, target_uuid)
);

CREATE TABLE IF NOT EXISTS homes (
  uuid   VARCHAR(36)  NOT NULL,
  name   VARCHAR(32)  NOT NULL,
  server VARCHAR(64)  NOT NULL,
  world  VARCHAR(64)  NOT NULL,
  x      DOUBLE       NOT NULL,
  y      DOUBLE       NOT NULL,
  z      DOUBLE       NOT NULL,
  yaw    FLOAT        NOT NULL,
  pitch  FLOAT        NOT NULL,
  PRIMARY KEY (uuid, name)
);

CREATE TABLE IF NOT EXISTS last_locations (
  uuid   VARCHAR(36) PRIMARY KEY,
  server VARCHAR(64) NOT NULL,
  world  VARCHAR(64) NOT NULL,
  x      DOUBLE      NOT NULL,
  y      DOUBLE      NOT NULL,
  z      DOUBLE      NOT NULL,
  yaw    FLOAT       NOT NULL,
  pitch  FLOAT       NOT NULL
);

-- On startup TeleHop migrates 1.x TPA timestamps and numbered home slots where present.
-- See LEGACY.md for the upgrade, checks and recovery of conflicting home names.

CREATE TABLE IF NOT EXISTS spawn_backends (
  server VARCHAR(64) PRIMARY KEY,
  routing_enabled BOOLEAN NOT NULL,
  hub_command BOOLEAN NOT NULL,
  default_world VARCHAR(255) NOT NULL,
  default_x DOUBLE NOT NULL, default_y DOUBLE NOT NULL, default_z DOUBLE NOT NULL,
  default_yaw FLOAT NOT NULL, default_pitch FLOAT NOT NULL
);
CREATE TABLE IF NOT EXISTS spawn_routes (
  server VARCHAR(64) NOT NULL,
  source_world VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  target_server VARCHAR(64) NOT NULL,
  target_spawn VARCHAR(32),
  PRIMARY KEY (server, source_world)
);
CREATE TABLE IF NOT EXISTS named_spawns (
  id VARCHAR(32) PRIMARY KEY,
  server VARCHAR(64) NOT NULL,
  world VARCHAR(255) NOT NULL,
  x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL,
  yaw FLOAT NOT NULL, pitch FLOAT NOT NULL,
  restricted BOOLEAN NOT NULL,
  revision CHAR(36) NOT NULL,
  INDEX spawn_owner (server)
);
