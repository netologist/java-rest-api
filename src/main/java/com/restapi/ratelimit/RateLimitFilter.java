package com.restapi.ratelimit;

import com.restapi.problem.RateLimitExceededException;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token Bucket Rate Limiting Filter with standard RateLimit-* response headers.
 */
@Component
@Order(5)
public class RateLimitFilter implements Filter {

    public static class TokenBucket {
        private final long capacity;
        private final double refillRateTokensPerSec;
        private final AtomicLong tokens;
        private final AtomicLong lastRefillNanos;

        public TokenBucket(long capacity, double refillRateTokensPerSec) {
            this.capacity = capacity;
            this.refillRateTokensPerSec = refillRateTokensPerSec;
            this.tokens = new AtomicLong(capacity);
            this.lastRefillNanos = new AtomicLong(System.nanoTime());
        }

        public synchronized boolean tryConsume() {
            refill();
            if (tokens.get() > 0) {
                tokens.decrementAndGet();
                return true;
            }
            return false;
        }

        public synchronized long getRemainingTokens() {
            refill();
            return tokens.get();
        }

        public long getCapacity() { return capacity; }

        private void refill() {
            long now = System.nanoTime();
            long last = lastRefillNanos.get();
            long elapsed = now - last;
            if (elapsed <= 0) return;

            double addTokens = (elapsed / 1_000_000_000.0) * refillRateTokensPerSec;
            if (addTokens >= 1.0 && lastRefillNanos.compareAndSet(last, now)) {
                tokens.set(Math.min(capacity, tokens.get() + (long) addTokens));
            }
        }
    }

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final long defaultCapacity = 100;
    private final double defaultRefillRate = 10.0; // 10 tokens per second

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Rate limit by X-API-Key or client IP address
        String clientKey = httpRequest.getHeader("X-API-Key");
        if (clientKey == null || clientKey.isBlank()) {
            clientKey = httpRequest.getRemoteAddr();
        }

        TokenBucket bucket = buckets.computeIfAbsent(clientKey, k -> new TokenBucket(defaultCapacity, defaultRefillRate));

        boolean permitted = bucket.tryConsume();
        long remaining = bucket.getRemainingTokens();

        // Standard IETF RateLimit headers
        httpResponse.setHeader("RateLimit-Limit", String.valueOf(bucket.getCapacity()));
        httpResponse.setHeader("RateLimit-Remaining", String.valueOf(remaining));
        httpResponse.setHeader("RateLimit-Reset", "1");

        if (!permitted) {
            throw new RateLimitExceededException("Rate limit exceeded. Please try again later.", Duration.ofSeconds(1));
        }

        chain.doFilter(request, response);
    }
}
