package com.restapi.pagination;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Opaque Base64 Cursor representation (Category 10 & 13).
 * Encodes timestamp and ID to prevent index scan degradation.
 */
public record Cursor(Instant timestamp, String id) {

    public String encode() {
        String raw = timestamp.toEpochMilli() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Optional<Cursor> decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encoded);
            String raw = new String(bytes, StandardCharsets.UTF_8);
            String[] parts = raw.split(":", 2);
            if (parts.length != 2) return Optional.empty();

            long millis = Long.parseLong(parts[0]);
            return Optional.of(new Cursor(Instant.ofEpochMilli(millis), parts[1]));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
