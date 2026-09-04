package com.restapi.webhooks;

import java.time.Instant;

/**
 * Standard Webhook Notification Payload (Java Record).
 */
public record WebhookPayload(
        String eventId,
        String eventType,
        String resourceId,
        Object data,
        Instant timestamp
) {}
