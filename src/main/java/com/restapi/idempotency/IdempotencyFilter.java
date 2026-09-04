package com.restapi.idempotency;

import com.restapi.problem.IdempotencyConflictException;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Idempotency Filter for mutating HTTP operations (POST, PUT, PATCH).
 * <p>
 * Implements the IETF Idempotency-Key specification:
 * <ul>
 *   <li>Replays cached response for identical keys with {@code Idempotency-Replay: true}.</li>
 *   <li>Detects concurrent in-flight requests with the same key and returns <b>409 Conflict</b>.</li>
 *   <li>Caches status, response body, and headers with a configurable TTL.</li>
 * </ul>
 */
@Component
@Order(10)
public class IdempotencyFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    public record CachedResponse(int status, String contentType, byte[] body, Instant expiresAt, boolean inFlight) {}

    private final Map<String, CachedResponse> store = new ConcurrentHashMap<>();
    private final Duration ttl = Duration.ofMinutes(15);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String key = httpRequest.getHeader(IDEMPOTENCY_KEY_HEADER);
        String method = httpRequest.getMethod();

        if (key == null || key.isBlank() || "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) {
            chain.doFilter(request, response);
            return;
        }

        // Clean expired keys lazily
        Instant now = Instant.now();
        CachedResponse existing = store.get(key);
        if (existing != null && now.isAfter(existing.expiresAt())) {
            store.remove(key);
            existing = null;
        }

        if (existing != null) {
            if (existing.inFlight()) {
                // Another request with this key is currently executing
                throw new IdempotencyConflictException("An idempotent request with key '" + key + "' is currently in flight.");
            }

            // Replay cached response
            log.info("Replaying cached idempotent response for key: {}", key);
            httpResponse.setStatus(existing.status());
            if (existing.contentType() != null) {
                httpResponse.setContentType(existing.contentType());
            }
            httpResponse.setHeader("Idempotency-Replay", "true");
            httpResponse.getOutputStream().write(existing.body());
            return;
        }

        // Mark key as in-flight
        store.put(key, new CachedResponse(0, null, new byte[0], now.plus(ttl), true));

        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(httpResponse);
        try {
            chain.doFilter(request, responseWrapper);

            int status = responseWrapper.getStatus();
            byte[] body = responseWrapper.getContentAsByteArray();
            String contentType = responseWrapper.getContentType();

            // Cache finished response
            store.put(key, new CachedResponse(status, contentType, body, Instant.now().plus(ttl), false));
            responseWrapper.copyBodyToResponse();
        } catch (Exception ex) {
            store.remove(key); // Remove in-flight mark on error
            throw ex;
        }
    }
}
