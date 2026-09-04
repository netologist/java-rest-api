package com.restapi.model;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Immutable Order Entity modeled as a Java Record.
 *
 * @param id        Unique order identifier
 * @param status    Current lifecycle status
 * @param amount    Monetary amount (BigDecimal for financial precision)
 * @param currency  ISO-4217 Currency code
 * @param version   Version number for optimistic concurrency control (ETags / If-Match)
 * @param createdAt Creation timestamp
 * @param updatedAt Last update timestamp
 */
public record Order(
        String id,
        OrderStatus status,
        BigDecimal amount,
        String currency,
        int version,
        Instant createdAt,
        Instant updatedAt
) {
    public Order {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Order ID cannot be empty");
        Objects.requireNonNull(status, "Status cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("Amount must be positive");
        if (currency == null || currency.isBlank()) currency = "USD";
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = createdAt;
    }

    /**
     * Computes RFC 9110 compliant ETag for conditional requests.
     */
    public String eTag() {
        String raw = String.format("%s:%s:%d:%s:%s", id, status, version, amount.toPlainString(), currency);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes());
            return "\"" + HexFormat.of().formatHex(hash).substring(0, 16) + "\"";
        } catch (Exception e) {
            return "\"" + Integer.toHexString(raw.hashCode()) + "\"";
        }
    }

    public Order withStatusAndVersion(OrderStatus newStatus, int newVersion) {
        return new Order(id, newStatus, amount, currency, newVersion, createdAt, Instant.now());
    }

    public Order withAmount(BigDecimal newAmount, int newVersion) {
        return new Order(id, status, newAmount, currency, newVersion, createdAt, Instant.now());
    }
}
