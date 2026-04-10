package com.telehop.common.model;

import java.time.Instant;
import java.util.UUID;

public record TpaRequestRecord(
        UUID senderUuid,
        UUID targetUuid,
        TpaType type,
        Instant expiry
) {
}
