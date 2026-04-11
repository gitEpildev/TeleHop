package com.telehop.common.model;

public record HomeRecord(
        String uuid,
        int slot,
        String server,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {}
