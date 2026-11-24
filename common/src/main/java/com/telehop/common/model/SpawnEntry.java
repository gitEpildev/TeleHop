package com.telehop.common.model;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** A published revision of a destination owned by one backend. */
public record SpawnEntry(String id, String server, SpawnPoint point, UUID revision) {
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,31}");

    public boolean restricted() { return point.restricted(); }

    public static String normalise(String input) {
        String id = input.toLowerCase(Locale.ROOT);
        if (!ID.matcher(id).matches()) throw new IllegalArgumentException("Invalid spawn ID: " + input);
        return id;
    }
}
