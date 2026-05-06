# Idempotency-Key Support — Design Spec

**Issue:** [#4](https://github.com/philipz/token-billing/issues/4)
**SRS refs:** SRS-F-3, SRS-F-6 step 6, SRS-F-13, NFR-PORT-1, NFR-LOG-4, AC7
**Branch base:** `philipz/agent-sdlc`
**Date:** 2026-05-06

## 1. Overview

`POST /api/usage` accepts an optional `Idempotency-Key` HTTP header. When present, a 24-hour replay window protects clients from duplicate billing on retries:

- Same `(customerId, key)` within 24 h, **same** `(promptTokens, completionTokens)` → return the original bill body with HTTP **200** and response header `Idempotent-Replayed: true`. No new row, no quota deduction.
- Same `(customerId, key)` within 24 h, **different** payload → HTTP **422** `Idempotency-Key reused with different payload`.
- Same key after > 24 h → treated as a new request.
- Header absent → existing happy path (every POST creates a new bill).

The change adds two columns to `bills`, a partial unique index on PostgreSQL with a portable plain-`UNIQUE` fallback on H2, header validation per the SRS regex, and replay/mismatch logic in the service layer.

## 2. Scope

In scope (issue #4 acceptance criteria 1–10):

- V2 Flyway migration: `bills.idempotency_key VARCHAR(255)` (nullable) + `bills.created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`.
- PostgreSQL partial unique index `(customer_id, idempotency_key) WHERE idempotency_key IS NOT NULL`.
- H2 fallback: full `UNIQUE (customer_id, idempotency_key)` with documented divergence (NFR-PORT-1).
- Header regex validation `^[A-Za-z0-9_\-]{8,255}$` → 400 on mismatch.
- 24-h lookup, replay (200 + `Idempotent-Replayed: true`), mismatch (422), no-header path (existing behaviour).
- AC7 integration tests on PostgreSQL (Testcontainers) and H2.

Out of scope (deliberately deferred):

- **Concurrent race handling.** `select-then-insert` has a small race window; relying on the DB unique index as the last line of defence (current generic 500 fallback). SRS-F-11 / pessimistic lock will close this in a separate issue.
- **`Clock` injection.** Existing service uses hardcoded `Instant.now()` and `LocalDate.now(UTC)`. Refactoring to a Clock bean is a follow-up that should be applied uniformly across the service.
- **JWT / scope checks (NFR-SEC-1/2).** Out of scope for issue #4.
- **Metrics counter `billing.idempotency.replay.total`.** NFR-OBS-2 — separate observability ticket.

## 3. Architecture Decisions

| # | Decision | Choice | Rationale |
|---|---|---|---|
| 1 | Multi-dialect Flyway layout | Vendor sub-directories (`db/migration/{common,postgresql,h2}`) with `spring.flyway.locations` template | Standard Flyway pattern; clearest file boundary; reusable for future migrations. |
| 2 | Header format validation layer | Bean Validation `@Pattern` on the `@RequestHeader` parameter | Reuses existing `ConstraintViolationException` pipeline in `GlobalExceptionHandler`; consistent with `UsageRequest` field validation. |
| 3 | Service ↔ controller replay signal | New `BillResult(BillResponse body, boolean replayed)` record returned by service | Keeps controller thin; no exception abuse for non-error replay; DTO unchanged. |
| 4 | Concurrent race | Not handled in this issue | YAGNI; SRS-F-11 / pessimistic lock will own this. DB unique index acts as last line of defence. |
| 5 | 24-h cutoff "now" | Hardcoded `Instant.now().minus(Duration.ofHours(24))` | Consistent with existing service style; Clock injection is a separate refactor. Boundary tests use ±1 min fixtures, jitter-tolerant. |

## 4. Migration / DB Schema

### 4.1 Directory layout

```
src/main/resources/db/migration/
├── common/
│   ├── V1__Create_tables.sql                    # moved from db/migration/
│   └── V2__Add_idempotency_columns.sql          # new
├── postgresql/
│   └── V3__Add_idempotency_unique_index.sql     # new
└── h2/
    └── V3__Add_idempotency_unique_index.sql     # new
```

### 4.2 `application.yml`

```yaml
spring:
  flyway:
    locations: classpath:db/migration/common,classpath:db/migration/${spring.flyway.vendor:postgresql}
```

`application-h2.yml` (test profile) overrides `spring.flyway.vendor: h2`.

### 4.3 `common/V2__Add_idempotency_columns.sql`

```sql
ALTER TABLE bills
    ADD COLUMN idempotency_key VARCHAR(255),
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX idx_bills_created_at ON bills (created_at);
```

### 4.4 `postgresql/V3__Add_idempotency_unique_index.sql`

```sql
-- Partial unique index: enforce uniqueness only when idempotency_key is set.
-- Multiple bills with NULL key (header absent) for the same customer remain allowed.
-- H2 lacks WHERE-clause partial indexes; see h2/V3 for the portable fallback (NFR-PORT-1).
CREATE UNIQUE INDEX idx_bills_idempotency
    ON bills (customer_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
```

### 4.5 `h2/V3__Add_idempotency_unique_index.sql`

```sql
-- H2 2.x does not support partial unique indexes (WHERE clause).
-- Fallback: full UNIQUE constraint on (customer_id, idempotency_key).
-- Divergence vs PostgreSQL (per NFR-PORT-1):
--   PostgreSQL allows multiple NULL idempotency_key per customer.
--   H2 follows the SQL standard (two NULLs are distinct), so practical
--   behaviour matches; documenting explicitly in case of dialect drift.
ALTER TABLE bills ADD CONSTRAINT uq_bills_idempotency
    UNIQUE (customer_id, idempotency_key);
```

### 4.6 `build.gradle`

```gradle
testRuntimeOnly 'com.h2database:h2'
```

## 5. Entity / Repository

### 5.1 `Bill` entity additions

```java
@Column(name = "idempotency_key", length = 255)
private String idempotencyKey;     // nullable

@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
private Instant createdAt;          // DB DEFAULT CURRENT_TIMESTAMP fills it
```

`insertable = false` so JPA does not write a NULL `created_at`; the DB DEFAULT supplies it. `calculatedAt` (business timestamp) is unchanged.

The all-args constructor and getters are extended; the persistence-time `Bill` factory in `UsageService` sets `idempotencyKey` (may be null) and lets `createdAt` default.

### 5.2 `BillRepository` query

```java
@Query("SELECT b FROM Bill b " +
       "WHERE b.customer.id = :customerId " +
       "AND b.idempotencyKey = :key " +
       "AND b.createdAt >= :cutoff " +
       "ORDER BY b.createdAt DESC")
List<Bill> findActiveIdempotentBills(
    @Param("customerId") String customerId,
    @Param("key") String idempotencyKey,
    @Param("cutoff") Instant cutoff);
```

`cutoff = Instant.now().minus(Duration.ofHours(24))` is computed in the service, keeping JPQL portable across PostgreSQL and H2 (no native `interval` syntax). Returning `List` defends against the rare race where the partial unique index has not yet asserted itself; service consumes via `.stream().findFirst()`.

## 6. Service Flow

### 6.1 New types

```java
package org.tw.token_billing.dto;
public record BillResult(BillResponse body, boolean replayed) {}
```

```java
package org.tw.token_billing.exception;
public class IdempotencyKeyMismatchException extends RuntimeException {
    private final String customerId;
    private final String idempotencyKey;
    // ctor + getters
}
```

### 6.2 Updated signature

```java
public BillResult calculateBill(UsageRequest request, String idempotencyKey)
```

`idempotencyKey` is nullable.

### 6.3 Pipeline

Steps below are numbered to match SRS-F-6. Steps 1–3 (JWT, body parsing, field/header format) are handled before the service is reached: by the resource server filter (out of scope for this issue), Spring's `HttpMessageConverter`, and Bean Validation respectively. Step 7 (pessimistic lock) is owned by SRS-F-11 / a separate issue (§10).

```
SRS-F-6 step 4 — customer existence:
   if !customerRepository.existsById(customerId)
       throw CustomerNotFoundException;        // -> 404

SRS-F-6 step 5 — active subscription:
   List<CustomerSubscription> subs = findAllActiveSubscriptions(...);
   subs.size() == 0 -> NoActiveSubscriptionException;        // 409
   subs.size() >  1 -> log.error + MultipleActiveSubscriptionsException;  // 500

SRS-F-6 step 6 — idempotency lookup:
   if (idempotencyKey != null) {
       List<Bill> hits = billRepository.findActiveIdempotentBills(
           customerId, idempotencyKey, Instant.now().minus(Duration.ofHours(24)));
       if (!hits.isEmpty()) {
           Bill existing = hits.get(0);
           if (payloadMatches(existing, request)) {
               log.info("Idempotency replay hit customerId={} keyPrefix={}",
                        customerId, prefix8(idempotencyKey));
               return new BillResult(toResponse(existing), true);   // 200 + replayed
           } else {
               throw new IdempotencyKeyMismatchException(customerId, idempotencyKey); // 422
           }
       }
   }

SRS-F-6 step 8 — calculate and persist (step 7 lock omitted, see §10):
   compute totalTokens / quota / overage / charge;
   Bill bill = new Bill(..., idempotencyKey);  // createdAt left to DB default
   billRepository.save(bill);
   return new BillResult(toResponse(bill), false);                  // 201
```

`payloadMatches` compares only `(promptTokens, completionTokens)` per SRS-F-3.

`prefix8` truncates to first 8 characters per NFR-LOG-4.

### 6.4 Implication of step ordering

If a customer's active subscription expires after an idempotent bill was recorded, a replay request within 24 h will hit `409 No active subscription` at step 2 **before** the idempotency lookup at step 3. This matches SRS-F-6 verbatim and is documented as expected behaviour.

## 7. Controller / Response Building

```java
@PostMapping("/usage")
public ResponseEntity<BillResponse> submitUsage(
        @Valid @RequestBody UsageRequest request,
        @RequestHeader(value = "Idempotency-Key", required = false)
            @Pattern(regexp = "^[A-Za-z0-9_\\-]{8,255}$",
                     message = "Invalid Idempotency-Key format")
            String idempotencyKey) {

    BillResult result = usageService.calculateBill(request, idempotencyKey);

    HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
    ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
    if (result.replayed()) {
        builder.header("Idempotent-Replayed", "true");
    }
    return builder.body(result.body());
}
```

The `Idempotent-Replayed` header is added only on replay; never set to `false`. Body schema is unchanged.

## 8. Exception Handling / RFC 7807

### 8.1 `GlobalExceptionHandler.handleConstraintViolation` — extend mapping

Add field constant and branch alongside the existing `customerId` and token rules:

```java
private static final String IDEMPOTENCY_KEY_FIELD = "idempotencyKey";

// inside the for-loop:
if (IDEMPOTENCY_KEY_FIELD.equals(field) && "Pattern".equals(annotation)) {
    return createProblemDetail(
        HttpStatus.BAD_REQUEST,
        "Invalid Idempotency-Key format",
        "Invalid Idempotency-Key format");
}
```

### 8.2 New 422 handler

```java
@ExceptionHandler(IdempotencyKeyMismatchException.class)
public ProblemDetail handleIdempotencyMismatch(IdempotencyKeyMismatchException ex) {
    String prefix = ex.getIdempotencyKey()
        .substring(0, Math.min(8, ex.getIdempotencyKey().length()));
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Idempotency-Key '" + prefix + "...' was reused with a different payload");
    pd.setType(DEFAULT_TYPE);
    pd.setTitle("Idempotency-Key reused with different payload");
    pd.setInstance(URI.create("/api/usage"));
    return pd;
}
```

The detail string carries only the first 8 characters of the key plus `"..."` so the response (and any logs that capture it) cannot leak the full key, in line with NFR-LOG-4.

## 9. Testing

### 9.1 Service unit (`UsageServiceTest`)

| Case | Setup | Assertion |
|---|---|---|
| Header absent | `idempotencyKey = null` | Service does not call `findActiveIdempotentBills`; replayed=false. |
| Replay hit, same payload | repo returns 1 bill with same `(prompt, completion)` | replayed=true; body equals mapping of existing bill; `billRepository.save` and `sumTotalTokensByCustomerIdAndMonthStart` not called. |
| Replay hit, different payload | repo returns 1 bill with diff payload | Throws `IdempotencyKeyMismatchException`; no `save`. |
| No replay (older than 24 h or no record) | repo returns `[]` | Falls through to compute + save; saved Bill carries the header value. |
| Cutoff argument | any | `ArgumentCaptor` confirms `cutoff` is within ±1 s of `Instant.now().minus(24 h)`. |

### 9.2 Controller WebMvcTest (`UsageControllerTest`)

| Case | Assertion |
|---|---|
| No header → existing 201 | unchanged |
| Header present, service returns replayed=true | Status 200, header `Idempotent-Replayed: true`, body equals service response. |
| Header present, service returns replayed=false | Status 201, no `Idempotent-Replayed` header. |
| Invalid format (`bad!key`) | 400 RFC 7807, title `Invalid Idempotency-Key format`. |
| 7-character key | 400 (regex `{8,255}` boundary). |
| Service throws `IdempotencyKeyMismatchException` | 422 RFC 7807, title `Idempotency-Key reused with different payload`, detail contains 8-char prefix `+ "..."`. |

### 9.3 Repository (`BillRepositoryTest`) — Testcontainers PostgreSQL

Backdating `created_at` requires a native `UPDATE` after `entityManager.persist`, since the `Bill` entity declares the column `insertable = false` and the DB DEFAULT supplies it on INSERT. Tests use `entityManager.getEntityManager().createNativeQuery("UPDATE bills SET created_at = ? WHERE id = ?")` to set the desired timestamp.

| Case | Setup | Assertion |
|---|---|---|
| Active idempotent within window | `created_at = now() - 23 h` | Returns 1 bill. |
| Outside window | `created_at = now() - 25 h` | Returns `[]`. |
| Cross-customer isolation | Same key, different customers | Each query returns only its own. |
| NULL key not matched | Persist bill without key | Not retrieved by any keyed query. |
| Partial unique index PG | Two non-NULL `(customer, key)` collisions | Second INSERT throws `DataIntegrityViolationException`. |
| NULLs allowed in PG | Two bills, same customer, both NULL key | Both INSERT succeed. |

### 9.4 H2 portability smoke test (`BillRepositoryH2Test`)

`@DataJpaTest` on the default H2 datasource with `application-h2.yml`. Asserts:

- V2 + V3 H2 migrations apply.
- A bill with idempotency key persists and is retrievable.
- A second INSERT with the same `(customer, key)` raises `DataIntegrityViolationException` (full UNIQUE).

H2 does not need to mirror PostgreSQL's "two NULLs allowed" semantics; the divergence is documented in the H2 migration comment.

### 9.5 24-h boundary

Service unit tests cover the in-window / out-of-window logical boundary via mocked repository returns. Real-SQL boundary is owned by §9.3. ±1 minute fixtures are jitter-tolerant.

## 10. Known Limitations

- **Concurrent race:** `select-then-insert` has a window in which two simultaneous identical requests can both compute. The first persists; the second is rejected by the DB unique index and surfaces as a 500 from the generic exception handler. SRS-F-11 (pessimistic lock) closes this gap.
- **NULL semantics divergence:** PostgreSQL's partial index allows multiple NULL keys per customer; H2's plain UNIQUE follows SQL-standard NULL-distinct semantics. Practical behaviour is equivalent for the issue's scope but is documented in the H2 migration.
- **Metrics:** `billing.idempotency.replay.total` (NFR-OBS-2) is not emitted by this issue; observability ticket will add it.
- **Clock injection:** Hardcoded `Instant.now()` continues; a follow-up should refactor `UsageService` to a `Clock` bean across all time references.
