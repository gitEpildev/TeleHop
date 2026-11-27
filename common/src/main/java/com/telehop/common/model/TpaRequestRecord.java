package com.telehop.common.model;

import java.time.Instant;
import java.util.UUID;

public record TpaRequestRecord(
        UUID senderUuid,
        UUID targetUuid,
        TpaType type,
        Instant sentAt
) {
    public TpaRequestRecord {
        // SQL and plugin messages store milliseconds, so a reloaded request must compare equal.
        sentAt = Instant.ofEpochMilli(sentAt.toEpochMilli());
    }

    /** Returns true when this request has exceeded the given timeout. */
    public boolean isExpired(long timeoutSeconds) {
        return Instant.now().isAfter(sentAt.plusSeconds(timeoutSeconds));
    }
}
