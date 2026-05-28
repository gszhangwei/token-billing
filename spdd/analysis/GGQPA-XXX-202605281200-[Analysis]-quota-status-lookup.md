# SPDD Analysis: Quota Status Lookup

## Original Business Requirement

# INVEST Analysis

## Abstract Task

Provide a read-only API for clients to inspect a customer's current monthly quota status before submitting token usage. The lookup resolves the customer's active subscription plan, aggregates tokens consumed in the current calendar month using the same rules as usage billing, and returns a snapshot sufficient to decide whether to allow or throttle another request.

**Primary operations:** lookup by customer ID; return quota, usage, remaining allowance, and overage rate.

**Constraints:** reuse existing subscription resolution and monthly usage calculation (no new billing algorithm); no bill history or individual bill records; no customer CRUD; unknown customers behave identically to usage billing.

**Complexity:** Low — single read endpoint, shared domain logic with Story 1.

## INVEST Checklist

| Criterion | Assessment |
|-----------|------------|
| **Independent** | Yes — delivers standalone value (proactive throttling/warnings) and can ship after Story 1 without further stories. |
| **Negotiable** | Yes — response detail can be refined in analysis/design without changing the core lookup intent. |
| **Valuable** | Yes — API clients can warn users or throttle before incurring overage. |
| **Estimable** | Yes — one endpoint, shared domain rules, ~1–3 days. |
| **Small** | Yes — one functional capability with 2–3 related behaviors (lookup, not-found, quota math). |
| **Testable** | Yes — concrete Given-When-Then ACs with numeric examples. |

## Split Strategy

No split needed. One read-only endpoint with a single business purpose; splitting by technical layer would violate SPDD split rules.

---

## [STORY-BILLING-002] Quota Status Lookup

### Background

The LLM API platform charges customers based on token consumption against monthly included quotas (see `requirements/token-usage-billing-story.md`). Clients that submit usage via POST /api/usage currently learn quota impact only after a bill is calculated. Product teams need a way to check remaining allowance **before** submitting usage so they can warn end users or throttle requests proactively.

### Business Value

1. **Proactive quota awareness:** Clients can surface remaining allowance without submitting usage.
2. **Reduced surprise overage:** Throttling or warnings before submission lowers unintended overage charges.
3. **Consistent domain rules:** Quota status reflects the same subscription and monthly usage logic as billing, avoiding conflicting numbers.

### Dependencies and Assumptions

- **Depends on Story 1 (Token Usage Billing):** Customers, active subscriptions, plans, and persisted bills must exist. Monthly usage is derived from bill records in the current calendar month using the same aggregation rules as billing.
- **Assumes** calendar-month boundaries and active-subscription resolution match usage billing (no separate quota-reset automation).
- **Assumes** seed customers (e.g. CUST-001 with 100,000 monthly quota and $0.02 overage per 1K tokens) remain available for examples and tests.

### Scope In

* Read-only quota status lookup by customer ID.
* Return a quota snapshot including: customer identifier, active plan monthly quota, tokens used in the current calendar month, remaining quota (floored at zero), and overage rate from the active plan.
* Unknown customer → HTTP 404 with message "Customer not found" (same behavior as usage billing).
* Successful lookup → HTTP 200 with information sufficient for a client to decide whether to allow another request.

### Scope Out

* Submitting token usage or calculating charges (Story 1).
* Customer create, update, or delete.
* Bill history queries or exposure of individual bill records.
* Automated monthly quota reset jobs.
* New billing algorithms or pricing models beyond what Story 1 already defines.

### Acceptance Criteria (ACs)

1. **Unknown customer**
   **Given** a customer ID that does not exist
   **When** a client requests quota status for that customer
   **Then** return HTTP 404 with message "Customer not found".

2. **Successful lookup within quota**
   **Given** customer CUST-001 has an active plan with 100,000 monthly quota and $0.02 overage per 1,000 tokens, and 60,000 tokens have been used in the current calendar month
   **When** a client requests quota status for CUST-001
   **Then** return HTTP 200 showing monthly quota 100,000, tokens used this month 60,000, remaining quota 40,000, and overage rate $0.02 per 1,000 tokens.

3. **Remaining quota floored at zero**
   **Given** customer CUST-001 has an active plan with 100,000 monthly quota, and 120,000 tokens have been used in the current calendar month
   **When** a client requests quota status for CUST-001
   **Then** return HTTP 200 showing tokens used this month 120,000 and remaining quota 0 (not negative).

4. **No usage yet this month**
   **Given** customer CUST-001 has an active plan with 100,000 monthly quota and no bills in the current calendar month
   **When** a client requests quota status for CUST-001
   **Then** return HTTP 200 showing tokens used this month 0 and remaining quota 100,000.

5. **Active subscription alignment**
   **Given** a customer has an active subscription to a plan (effective on today's date per the same rules as usage billing)
   **When** a client requests quota status for that customer
   **Then** the returned monthly quota and overage rate match the active plan's values, not a lapsed or future plan.

6. **No bill history exposure**
   **Given** a customer has multiple bills in the current calendar month
   **When** a client requests quota status for that customer
   **Then** return HTTP 200 with aggregated tokens used this month only, and do not include bill IDs, individual bill amounts, or a list of bill records.

## Domain Concept Identification

### Existing Concepts (from codebase)

Story 1 is implemented. The domain model, persistence layer, and billing orchestration already exist under `org.tw.token_billing`.

- **Customer**: Identified by string ID (`customers` table). Existence check via `CustomerRepository.existsById`. Unknown customer raises `CustomerNotFoundException`, mapped to HTTP 404 with message `"Customer not found"` in `GlobalExceptionHandler`.
- **PricingPlan**: Holds `monthlyQuota` and `overageRatePer1k` (`pricing_plans` table). Seed plan `PLAN-STARTER` gives CUST-001 a 100,000 quota and $0.02/1K overage rate — matches AC examples.
- **CustomerSubscription**: Links customer to plan with `effectiveFrom` / optional `effectiveTo`. Active subscription resolved by `CustomerSubscriptionRepository.findActiveByCustomerId(customerId, asOfDate)` — filters `effectiveFrom <= asOfDate` and (`effectiveTo` is null or `effectiveTo >= asOfDate`), ordered by most recent `effectiveFrom`. Missing active subscription raises `NoActiveSubscriptionException` (HTTP 404, message `"No active subscription found"`).
- **Bill**: Persists each usage submission with `totalTokens` and `calculatedAt`. Serves as the ledger for current-month usage aggregation via `BillRepository.sumTotalTokensByCustomerIdAndCalculatedAtBetween`.
- **BillingService / BillingServiceImpl**: Story 1 write path. Already performs the subscription lookup, UTC calendar-month window calculation, monthly usage sum, and `remainingQuota = max(0, monthlyQuota - currentMonthUsage)`. This is the authoritative logic the quota lookup must align with.
- **UsageController**: Single write endpoint `POST /api/usage` returning HTTP 201.
- **ErrorResponse**: Consistent `{ "message": "..." }` error shape used across endpoints.

Relationships unchanged: Customer → active CustomerSubscription → PricingPlan; current-month usage derived from Bill rows for that customer.

### New Concepts Required

No new database entities or tables. The gap is **read-only application behavior**:

- **Quota status lookup flow**: Accept customer ID, validate existence, resolve active plan, aggregate current-month usage, compute remaining quota, return snapshot (no persistence).
- **Quota snapshot response**: A read model distinct from `BillResponse` — exposes quota, usage, remaining allowance, and overage rate without bill identity, charges, or bill lists.

Optional internal refactor (not a domain concept): extract shared subscription resolution and monthly-usage calculation so billing and quota lookup cannot drift.

### Key Business Rules

- **Current-month usage** = sum of `total_tokens` from bills for the customer where `calculated_at` falls in the current calendar month (same as Story 1; implemented in `BillingServiceImpl` using UTC month boundaries).
- **Remaining quota** = `monthlyQuota - currentMonthUsage`, floored at 0 (already applied in billing before quota consumption).
- **Active subscription** = subscription effective on lookup date (`LocalDate.now(UTC)` in billing); most recent `effectiveFrom` when multiple match.
- **Unknown customer** → HTTP 404, exact message `"Customer not found"` (reuse existing exception and handler).
- **Successful lookup** → HTTP 200 with customer ID, monthly quota, tokens used this month, remaining quota, and overage rate (explicit in scope; no bill records).
- **No new billing algorithm**: Lookup is a projection of existing rules, not a separate calculation path.
- **Implicit from Story 1 (not in Story 2 ACs)**: Customer exists but has no active subscription → currently HTTP 404 `"No active subscription found"` in billing. Story 2 does not specify this case; alignment with billing is the natural default.

## Strategic Approach

### Solution Direction

Add a single read-only REST endpoint for quota status lookup by customer ID, wired through the existing layered architecture:

1. Validate customer exists (same check as billing).
2. Resolve customer → active subscription → pricing plan (same rules and repositories as billing).
3. Aggregate current-month token usage from persisted bills (same query and UTC month window as billing).
4. Compute remaining quota with zero floor.
5. Return HTTP 200 with quota snapshot (no write, no bill creation).

High-level flow: **HTTP GET request → controller → service (read path) → existing JPA repositories → PostgreSQL**.

Story 1 patterns (`UsageController`, `BillingService`, `GlobalExceptionHandler`, repository queries) provide the template. No Flyway migration is expected unless the REASONS phase introduces a supporting type only in code.

### Key Design Decisions

| Decision | Trade-offs | Recommendation |
|----------|------------|----------------|
| Reuse vs duplicate subscription/usage logic | Duplicating in a new service risks drift when billing rules change. Extracting shared logic adds a small refactor to Story 1 code. | Extract or delegate to shared internal logic used by both `submitUsage` and quota lookup — guarantees AC5 alignment and satisfies "same rules" constraint. |
| Endpoint shape (path vs query) | REST convention favors resource path (e.g. customer-scoped URL); query param is simpler but less discoverable. Story does not prescribe. | Customer ID in URL path under `/api` — consistent with resource-oriented APIs; exact path fixed in REASONS. |
| Service boundary | Extend `BillingService` vs separate `QuotaService`. | Extend or compose under existing billing service layer — same domain, same repositories; avoids parallel service sprawl for one read operation. |
| No active subscription behavior | Story 2 ACs only cover unknown customer 404. Billing returns 404 `"No active subscription found"`. | Reuse billing's behavior for consistency unless product explicitly wants a different status for lookup-only. |
| Response fields beyond AC minimum | Scope lists customer ID; ACs do not require plan ID/name. | Return AC-required fields plus `customerId`; omit plan metadata unless product expands scope — keeps response minimal and avoids scope creep. |
| Read transaction semantics | Read-only; no `@Transactional` write needed. | No transaction or read-only transaction; no persistence on success. |

### Alternatives Considered

- **Separate usage/counter table**: Rejected — bills already aggregate `total_tokens`; a new table violates "reuse existing rules" and requires schema change.
- **Expose bill list with client-side sum**: Rejected — explicitly scope out; also leaks bill history.
- **Cache quota snapshots**: Rejected — premature; story has no performance ACs; adds invalidation complexity.
- **Compute remaining quota only (omit usage/overage)**: Rejected — ACs require full snapshot for client throttling decisions.

## Risk & Gap Analysis

### Requirement Ambiguities

| Ambiguity | Notes |
|-----------|-------|
| HTTP method and URL path | Story says "lookup by customer ID" but not path. Recommend GET with customer ID as path segment under `/api`; confirm in REASONS Requirements. |
| No active subscription | Not in Story 2 ACs. Billing returns 404 `"No active subscription found"`. Recommend same behavior for lookup to keep numbers meaningful. |
| Missing or malformed customer ID in request | No AC. Empty or invalid path likely 404 (unknown) or 400; decide in REASONS Safeguards. |
| Plan row missing after subscription resolve | Billing throws `NoActiveSubscriptionException` if plan not found. Same path applies to lookup. |
| Timezone for "current calendar month" | Not stated in Story 2; billing uses UTC (`ZoneOffset.UTC`). Recommend UTC for lookup to match billing exactly. |
| Include `customerId` in 200 response | Scope In lists it; AC2–AC4 imply field values but do not name JSON keys. Field naming deferred to REASONS. |
| Overage rate display precision | DB stores `DECIMAL(10,4)`; AC says $0.02 per 1,000 tokens. Serialization scale to be fixed in REASONS Norms. |

### Edge Cases

| Scenario | Why it matters |
|----------|----------------|
| Customer over quota (usage > monthly quota) | AC3 — remaining must be 0, not negative. Billing already floors; lookup must mirror. |
| Zero bills this month | AC4 — usage 0, full quota remaining. |
| Multiple bills same month | AC6 — sum aggregates correctly; response must not expose individual bills. |
| Lookup immediately after POST /api/usage | Read should reflect newly persisted bill in same month (eventual consistency within same DB). |
| Overlapping subscriptions | Repository picks most recent `effectiveFrom`; AC5 depends on this rule matching billing. |
| Concurrent usage POST during quota GET | Quota snapshot is point-in-time; race acceptable for MVP (same as billing analysis noted for concurrent POSTs). |
| Month boundary (calculated_at at month edge) | UTC half-open interval `[monthStart, monthEnd)` in billing — lookup must use identical bounds. |
| Seed customer with no bills ever | AC4 path; CUST-001 with no May bills returns 0 used / 100,000 remaining. |

### Technical Risks

| Risk | Impact | Mitigation direction |
|------|--------|---------------------|
| Logic drift between billing and lookup | Clients see remaining quota that disagrees with next bill | Share subscription resolution, month window, and usage aggregation in one place. |
| Integer cast of usage sum | `BillingServiceImpl` casts `long` usage to `int` for remaining quota; overflow if usage exceeds `Integer.MAX_VALUE` | Same limitation as billing; acceptable for story scale; document if quotas stay bounded. |
| Test setup for AC2–AC4 | Need controlled current-month bill totals | Reuse Story 1 test patterns: insert bills with `calculated_at` in current UTC month or mock repository in unit tests. |
| Extending controller vs new controller | Minor structural choice | Either add GET to quota-focused controller or extend API namespace; follow existing `UsageController` / `@RequestMapping("/api")` convention. |

### Acceptance Criteria Coverage

| AC# | Description | Addressable? | Gaps/Notes |
|-----|-------------|--------------|------------|
| 1 | Unknown customer → 404, `"Customer not found"` | Yes | Reuse `CustomerNotFoundException` and existing handler. |
| 2 | Within quota snapshot (100K / 60K used / 40K remaining / $0.02) | Yes | Requires seeded bills totaling 60K in current UTC month for CUST-001; shared usage aggregation. |
| 3 | Remaining floored at zero when 120K used | Yes | Same formula as billing; test with over-quota bill sum. |
| 4 | No usage this month → 0 used, full remaining | Yes | No bills in month window, or sum returns 0. |
| 5 | Active subscription alignment | Yes | Reuse `findActiveByCustomerId` + plan load; test with lapsed/future subscription fixture if needed. |
| 6 | No bill history in response | Yes | Response DTO excludes bill fields; service returns aggregate only. |

All six ACs are addressable with the recommended approach. Open decisions (URL shape, no-subscription behavior, response field names) should be resolved in the REASONS Canvas before `/spdd-generate`.
