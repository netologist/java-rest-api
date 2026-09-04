package com.restapi.webhooks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Webhook HMAC-SHA256 Signer & Constant-Time Signature Verifier.
 * Defends against Timing Attacks.
 */
public final class WebhookSigner {

    private static final Logger log = LoggerFactory.getLogger(WebhookSigner.class);

    private WebhookSigner() {}

    public static String computeSignature(byte[] payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(payload);
            return "sha256=" + HexFormat.of().formatHex(raw);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to compute HMAC signature", ex);
        }
    }

    /**
     * Verifies the signature in constant-time using {@link MessageDigest#isEqual}.
     */
    public static boolean verifySignature(byte[] payload, String headerSignature, String secret) {
        if (payload == null || headerSignature == null || secret == null) {
            return false;
        }

        try {
            String expected = computeSignature(payload, secret);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    headerSignature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception ex) {
            log.error("Signature verification error: {}", ex.getMessage());
            return false;
        }
    }
}
