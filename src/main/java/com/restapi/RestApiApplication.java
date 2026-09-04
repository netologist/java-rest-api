package com.restapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Production REST API Reference Implementation in Modern Java 21+ and Spring Boot 3.4.
 * <p>
 * Key Capabilities Demonstrated:
 * <ul>
 *   <li><b>Java 21 Virtual Threads:</b> {@code spring.threads.virtual.enabled=true} for async jobs and web requests.</li>
 *   <li><b>Java Records:</b> Immutable DTOs, HATEOAS representations, and API payloads.</li>
 *   <li><b>RFC 7807 Problem Details:</b> Standardized machine-readable error responses.</li>
 *   <li><b>HATEOAS:</b> Richardson Maturity Level 3 with dynamic {@code _links}.</li>
 *   <li><b>ETags & Conditional Requests:</b> {@code If-None-Match}, {@code If-Match} optimistic locking.</li>
 *   <li><b>Idempotency-Key:</b> Response caching and in-flight conflict detection.</li>
 *   <li><b>Rate Limiting:</b> Token bucket with standard {@code RateLimit-*} headers.</li>
 *   <li><b>Async Jobs (202 Accepted):</b> Long-running background processing with polling endpoints.</li>
 *   <li><b>Webhooks:</b> HMAC-SHA256 signature generation and constant-time verification.</li>
 *   <li><b>Server-Sent Events (SSE):</b> Live event streaming over Virtual Threads.</li>
 * </ul>
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class RestApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestApiApplication.class, args);
    }
}
