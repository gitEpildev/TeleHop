package com.telehop.common.model;

public record WarpRecord(
        String name,
        String server,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {
}
