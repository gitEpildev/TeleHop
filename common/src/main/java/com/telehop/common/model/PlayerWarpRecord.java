package com.telehop.common.model;

public record PlayerWarpRecord(
        String ownerUuid,
        String name,
        String server,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        boolean isPublic
) {
}
