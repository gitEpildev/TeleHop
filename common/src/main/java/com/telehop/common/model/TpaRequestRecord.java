package com.telehop.common.model;

import java.time.Instant;
import java.util.UUID;

public record TpaRequestRecord(
        UUID senderUuid,
        UUID targetUuid,
        TpaType type,
        Instant sentAt
) {
    /** Returns true when this request has exceeded the given timeout. */
    public boolean isExpired(long timeoutSeconds) {
        return Instant.now().isAfter(sentAt.plusSeconds(timeoutSeconds));
    }
}
