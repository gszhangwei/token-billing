# Changelog

## [1.0.0] — 2026-05-31

Initial release of the Token Usage Billing API.

### Acceptance Criteria Delivered

| AC | Description | Status |
|---|---|---|
| AC1 | Customer not found → HTTP 404 (`title="Customer not found"`) | ✅ |
| AC2 | Negative token counts → HTTP 400 (`title="Token count cannot be negative"`) | ✅ |
| AC3 | In-quota billing: `totalCharge=0.00`, `overageTokens=0` | ✅ |
| AC4 | Overage billing: correct proration with HALF_EVEN rounding | ✅ |
| AC5 | Successful bill returns HTTP 201 with complete `BillResponse` body | ✅ |
| AC6 | No active subscription → HTTP 409 (`title="No active subscription"`) | ✅ |
| AC7 | Idempotency-Key: replay returns HTTP 200 + `Idempotent-Replayed: true`; mismatch returns HTTP 422 | ✅ |

### Non-Functional Requirements Delivered

| NFR | Description | Status |
|---|---|---|
| NFR-SEC-1 | OAuth2 JWT resource server; `POST /api/usage` requires `billing:write` scope | ✅ |
| NFR-SEC-2 | RFC 7807 ProblemDetail error responses for 401/403 | ✅ |
| NFR-AUDIT | Bills table is append-only; idempotency prevents duplicate rows | ✅ |
| NFR-LOCK | Pessimistic lock (5 s timeout) prevents concurrent overage anomalies | ✅ |
| NFR-PERF-1 | p95 latency ≤ 200 ms at 100 RPS — measured 6.25 ms (see `perf/results.md`) | ✅ |
| NFR-OBS-1 | `spring-boot-starter-actuator`; `/actuator/health` (DB check) and `/actuator/prometheus` | ✅ |
| NFR-OBS-2 | Custom metrics: `billing.requests.total`, `billing.overage.charge`, `billing.lock.contention.total`, `billing.idempotency.replay.total` | ✅ |
| NFR-OBS-3 | Health check includes database connectivity status | ✅ |
| NFR-LOG-1 | Per-request INFO structured-JSON log line: `requestId`, `customerId`, `promptTokens`, `completionTokens`, `billId`, `totalCharge`, `durationMs` | ✅ |
| NFR-LOG-2 | Errors logged at ERROR with stack trace; `customerId` present in MDC | ✅ |
| NFR-LOG-3 | `application-prod.yml` sets `spring.jpa.show-sql: false` | ✅ |
| NFR-LOG-4 | Idempotency-Key truncated to 8 chars in logs; JWTs never logged | ✅ |

### OpenAPI / Swagger

- Swagger UI: `/swagger-ui.html`
- OpenAPI JSON: `/v3/api-docs`
- OpenAPI YAML snapshot: `requirements/token-usage-billing/openapi.yaml`
- Spec covers all success (200, 201) and error (400, 401, 403, 404, 409, 422, 500, 503) responses with RFC 7807 schemas

### Database Migrations

| Version | Description |
|---|---|
| V1 | Create tables: `customers`, `pricing_plans`, `customer_subscriptions`, `bills`; seed data |
| V2 | Add `idempotency_key` and `created_at` columns to `bills` |
| V3 | Add partial unique index on `(customer_id, idempotency_key) WHERE idempotency_key IS NOT NULL` |

### Dependencies

| Dependency | Version | Purpose |
|---|---|---|
| Spring Boot | 3.5.0 | Framework |
| Spring Security OAuth2 Resource Server | managed | JWT authentication |
| Spring Data JPA | managed | Persistence |
| Flyway | managed | Database migrations |
| Micrometer + Prometheus | managed | Metrics |
| Logstash Logback Encoder | 8.0 | Structured JSON logging |
| springdoc-openapi-starter-webmvc-ui | 2.8.9 | OpenAPI / Swagger UI |
| H2 | managed | Test database |
| PostgreSQL | managed | Production database |
