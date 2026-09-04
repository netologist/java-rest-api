package com.restapi.controller;

import com.restapi.webhooks.WebhookPayload;
import com.restapi.webhooks.WebhookSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Inbound Webhook Receiver with HMAC-SHA256 Timing Attack Defense.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);
    private final String webhookSecret = "whsec_super_secret_test_key_12345";

    @PostMapping("/incoming")
    public ResponseEntity<Map<String, String>> handleIncomingWebhook(
            @RequestHeader(value = "X-Signature-256", required = false) String signature,
            @RequestBody byte[] rawPayload
    ) {
        if (signature == null || signature.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing X-Signature-256 header");
        }

        // Constant-time HMAC signature verification (Timing attack safe)
        if (!WebhookSigner.verifySignature(rawPayload, signature, webhookSecret)) {
            log.warn("Invalid webhook signature attempt detected");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid HMAC signature");
        }

        log.info("Webhook verified successfully. Payload length: {} bytes", rawPayload.length);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Webhook verified and processed"));
    }
}
