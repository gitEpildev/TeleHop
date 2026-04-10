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
  expiry      BIGINT      NOT NULL,
  PRIMARY KEY (sender_uuid, target_uuid)
);
