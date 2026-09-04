# 🌐 java-rest-api: Production REST API Reference Implementation in Modern Java & Spring Boot

A comprehensive, production-quality **REST API reference implementation** built in **Modern Java 21+** and **Spring Boot 3.4.x**.

This project translates and reimagines the patterns from the Go [`rest-api-with-code/`](../rest-api-with-code/) repository into idiomatic Modern Java, strictly utilizing **Java Records**, **Virtual Threads (Project Loom)**, **Streams API**, **Spring 6 RFC 7807 Problem Details**, and **Richardson Maturity Level 3 HATEOAS**.

---

## 📑 Table of Contents

1. [Architecture Overview & Filter Chain](#architecture-overview--filter-chain)
2. [Go vs Modern Java 21+ Architectural Comparison](#go-vs-modern-java-21-architectural-comparison)
3. [Core REST Patterns & Implementations](#core-rest-patterns--implementations)
   - [1. Richardson Maturity Level 3 & HATEOAS](#1-richardson-maturity-level-3--hateoas)
   - [2. RFC 7807 Problem Details (Structured Errors)](#2-rfc-7807-problem-details-structured-errors)
   - [3. HTTP Caching, ETags & Conditional Requests](#3-http-caching-etags--conditional-requests)
   - [4. Idempotency-Key Filter (Replay & Conflict Detection)](#4-idempotency-key-filter-replay--conflict-detection)
   - [5. Token Bucket Rate Limiter & IETF Headers](#5-token-bucket-rate-limiter--ietf-headers)
   - [6. Cursor-Based Pagination & Stream Slicing](#6-cursor-based-pagination--stream-slicing)
   - [7. Async Jobs (202 Accepted Pattern) with Virtual Threads](#7-async-jobs-202-accepted-pattern-with-virtual-threads)
   - [8. Inbound Webhooks & Constant-Time HMAC-SHA256](#8-inbound-webhooks--constant-time-hmac-sha256)
   - [9. Server-Sent Events (SSE) Live Streaming](#9-server-sent-events-sse-live-streaming)
   - [10. Security Headers & Request Correlation ID](#10-security-headers--request-correlation-id)
4. [API Endpoints Reference](#api-endpoints-reference)
5. [Running the Application & Tests](#running-the-application--tests)

---

## Architecture Overview & Filter Chain

The order in which filters and middlewares execute defines the security, resilience, and observability perimeter of every HTTP request:

```
Incoming Request
       │
       ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        Global Filter Chain                             │
│  1. CorrelationIdFilter      (HIGHEST_PRECEDENCE) → Injects X-Request-ID│
│  2. SecurityHeadersFilter    (OWASP) → HSTS, X-Frame-Options, CSP      │
│  3. RateLimitFilter          (Token Bucket) → RateLimit-* headers      │
│  4. IdempotencyFilter        (IETF) → Replay & 409 Conflict check      │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
               ┌───────────────────┼───────────────────┐
               │                   │                   │
    ┌──────────▼──────────┐ ┌──────▼──────┐   ┌────────▼────────┐
    │  /api/v1/orders     │ │/api/v1/jobs │   │/api/v1/webhooks │
    │ CRUD, HATEOAS, SSE, │ │202 Accepted │   │HMAC-SHA256      │
    │ ETags, State Machine│ │VirtualThread│   │Constant-Time    │
    └─────────────────────┘ └─────────────┘   └─────────────────┘
```

---

## Go vs Modern Java 21+ Architectural Comparison

| Feature / Pattern | Go Implementation (`rest-api-with-code/`) | Modern Java Implementation (`java-rest-api/`) | Advantage / Why Java Approach |
|:---|:---|:---|:---|
| **Domain Models** | Structs with JSON tags | **Java Records** (`record Order(...)`) | Immutability out of the box, zero boilerplate, canonical compact constructors. |
| **Concurrency Scaling** | Goroutines (`go func()`) | **Java 21 Virtual Threads** (`spring.threads.virtual.enabled=true`) | Millions of lightweight threads on JVM ForkJoinPool; synchronous style without thread bloat. |
| **Error Format** | Custom `Problem` struct | **Spring 6 Native `ProblemDetail` (RFC 7807)** | Standardized machine-readable `application/problem+json` format supported natively by framework. |
| **Hypermedia (HATEOAS)**| Manual `_links` map generation | **`HateoasHelper` & `HalResource<T>` Records** | Type-safe state-driven link generator fulfilling Richardson Maturity Level 3. |
| **Idempotency** | In-memory mutex map | **`IdempotencyFilter` with ContentCaching** | Wraps response stream, detects concurrent in-flight requests (409 Conflict), replays cached body with `Idempotency-Replay: true`. |
| **Rate Limiting** | Custom token bucket | **`RateLimitFilter` with IETF Headers** | Emits standard `RateLimit-Limit`, `RateLimit-Remaining`, `RateLimit-Reset`, and `Retry-After`. |
| **Pagination** | Base64 opaque cursor | **`Cursor` Record + Java Streams API** | Stream-based slicing and multi-field sorting without expensive SQL `OFFSET` scans. |
| **Async Jobs (202)** | Channel polling | **Virtual Thread Task Execution** | Spawns background worker on Virtual Thread, polls progress via REST, supports cancellation. |
| **Timing Attack Defense**| `crypto/subtle.ConstantTimeCompare`| `MessageDigest.isEqual(...)` | Constant-time byte comparison prevents side-channel brute-force attacks on webhook HMACs. |

---

## Core REST Patterns & Implementations

### 1. Richardson Maturity Level 3 & HATEOAS

```java
HalResource<Order> hal = HateoasHelper.wrapWithHal("", order);
```

For a `PENDING` order, the response dynamically publishes available state transitions:
```json
{
  "data": {
    "id": "ord_1",
    "status": "PENDING",
    "amount": 149.99,
    "currency": "USD",
    "version": 1
  },
  "_links": {
    "self":   { "href": "/api/v1/orders/ord_1", "method": "GET", "title": "Get order details" },
    "pay":    { "href": "/api/v1/orders/ord_1/pay", "method": "POST", "title": "Pay for this order" },
    "cancel": { "href": "/api/v1/orders/ord_1/cancel", "method": "POST", "title": "Cancel this order" },
    "update": { "href": "/api/v1/orders/ord_1", "method": "PUT", "title": "Update this order (requires If-Match)" }
  }
}
```
When an order transitions to `PAID`, `pay` is automatically removed from `_links`, guiding clients dynamically without hardcoded URL logic.

---

### 2. RFC 7807 Problem Details (Structured Errors)

Handled via `@RestControllerAdvice` emitting `Content-Type: application/problem+json`:

```json
{
  "type": "https://api.example.com/errors/precondition-failed",
  "title": "Precondition Failed",
  "status": 412,
  "detail": "If-Match ETag mismatch. Order was modified by another request.",
  "instance": "/api/v1/orders/ord_1",
  "timestamp": "2026-09-04T09:25:00Z"
}
```

---

### 3. HTTP Caching, ETags & Conditional Requests

- **Conditional GET (`If-None-Match`):** If client provides fresh ETag, server immediately returns **`304 Not Modified`** with zero response body, saving network bandwidth.
- **Optimistic Concurrency Control (`If-Match`):** On `PUT /orders/{id}`, if the client's `If-Match` header does not match current ETag, returns **`412 Precondition Failed`**, preventing lost updates.
- **Cache-Control:** Automatically set to `private, max-age=60, must-revalidate`.

---

### 4. Idempotency-Key Filter (Replay & Conflict Detection)

- When a request carries `Idempotency-Key: <UUID>`:
  - If identical request is currently executing -> Returns **`409 Conflict`**.
  - If request already completed -> Replays cached response body, headers, and status code with header **`Idempotency-Replay: true`**.
  - Otherwise processes request and caches response with TTL.

---

### 5. Token Bucket Rate Limiter & IETF Headers

Per-client rate limiting (by `X-API-Key` or IP). Populates standard headers on every response:
- `RateLimit-Limit: 100`
- `RateLimit-Remaining: 99`
- `RateLimit-Reset: 1`
- On limit exhaustion: Returns **`429 Too Many Requests`** with `Retry-After: 1`.

---

### 6. Cursor-Based Pagination & Stream Slicing

- Opaque Base64 cursor: `Cursor(Instant timestamp, String id)` encoded as `base64(timestamp:id)`.
- Eliminates the performance penalty of SQL `OFFSET 50000`.
- Response format:
  ```json
  {
    "data": [ ... ],
    "nextCursor": "MTcyNTQ0MTkwMDAwMDpvcmRfMg",
    "hasMore": true,
    "count": 10
  }
  ```

---

### 7. Async Jobs (202 Accepted Pattern) with Virtual Threads

- Submitting long-running work (`POST /api/v1/jobs`) returns:
  - **Status:** `202 Accepted`
  - **Headers:** `Location: /api/v1/jobs/{id}`, `Retry-After: 2`
- Processing executes on a lightweight Java 21 **Virtual Thread**.
- Clients poll `GET /api/v1/jobs/{id}` until status reaches `COMPLETED` or cancel with `DELETE /api/v1/jobs/{id}`.

---

### 8. Inbound Webhooks & Constant-Time HMAC-SHA256

- Webhooks carry `X-Signature-256: sha256=<hex>`.
- Signature is verified using `MessageDigest.isEqual(...)`, ensuring constant-time execution and preventing timing attacks.

---

### 9. Server-Sent Events (SSE) Live Streaming

- Clients connect to `GET /api/v1/orders/events`.
- Emits real-time SSE updates (`ORDER_CREATED`, `ORDER_PAID`, `ORDER_CANCELLED`) broadcast asynchronously via Virtual Threads.

---

## API Endpoints Reference

| Method | Path | Description | Key Headers / Features |
|---|---|---|---|
| `GET` | `/api/v1/orders` | List orders with cursor pagination | `?cursor=...&limit=10&status=PENDING` |
| `GET` | `/api/v1/orders/{id}` | Get order with HATEOAS & ETags | `If-None-Match`, returns 304 or 200 |
| `POST` | `/api/v1/orders` | Create order | `Idempotency-Key` (Optional), returns 201 |
| `PUT` | `/api/v1/orders/{id}` | Update order (Optimistic locking) | `If-Match` (Required), returns 412 on mismatch |
| `POST` | `/api/v1/orders/{id}/pay` | Pay for order | State transition (422 if invalid) |
| `POST` | `/api/v1/orders/{id}/cancel`| Cancel order | State transition (422 if invalid) |
| `GET` | `/api/v1/orders/events` | Live Server-Sent Events stream | `text/event-stream` |
| `POST` | `/api/v1/jobs` | Submit async background job | Returns `202 Accepted` + `Location` |
| `GET` | `/api/v1/jobs/{id}` | Poll async job progress | Polling endpoint |
| `DELETE` | `/api/v1/jobs/{id}` | Cancel in-flight job | Cancelation |
| `POST` | `/api/v1/webhooks/incoming`| Verify inbound webhook | `X-Signature-256` HMAC validation |

---

## Running the Application & Tests

### Prerequisites
- **Java 21+** (JDK 21 or 25)
- **Maven 3.9+**

### Run the Test Suite
Executes unit tests, MockMvc filter tests, Virtual Thread async tests, and HATEOAS validations:

```bash
cd java-rest-api
mvn test -o
```

Expected output:
```
[INFO] ---------------------< com.restapi:java-rest-api >----------------------
[INFO] Building java-rest-api 1.0.0
[INFO] Results:
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Launch the Server
```bash
cd java-rest-api
mvn spring-boot:run -o
```

Server starts on `http://localhost:8080`.
Actuator health probes available at `http://localhost:8080/actuator/health`.
