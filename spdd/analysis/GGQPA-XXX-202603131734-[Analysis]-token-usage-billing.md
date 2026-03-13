# SPDD Analysis: Token Usage Billing API

## Original Business Requirement

## Background
The LLM API platform charges customers based on token consumption. Customers have monthly included quotas; usage exceeding the quota is billed at an overage rate.

## Business Value
1. **Accurate Billing**: Calculate charges based on actual token consumption.
2. **Quota Management**: Track usage against included quotas.
3. **Revenue Capture**: Bill overage when customers exceed quotas.

## Scope In
* Implement POST /api/usage endpoint for submitting token usage and receiving calculated bills.
* Request fields:
  * Customer ID (required, must exist)
  * Prompt tokens (required, ≥ 0)
  * Completion tokens (required, ≥ 0)
* Calculate bill using customer's monthly quota, current month usage, and overage rate.

## Scope Out
* Customer CRUD operations.
* Historical bill queries.
* Monthly quota reset logic.

## Acceptance Criteria (ACs)
1. Validate Customer ID exists
   **Given** customer ID does not exist
   **When** backend receives request
   **Then** return HTTP 404, message "Customer not found".

2. Validate token counts are non-negative
   **Given** prompt tokens or completion tokens is negative
   **When** backend validates request
   **Then** return HTTP 400, message "Token count cannot be negative".

3. Bill within included quota
   **Given** customer has 100,000 monthly quota and 60,000 tokens used this month
   **When** submitting 30,000 tokens
   **Then** bill shows: 30,000 from quota, 0 overage, $0.00 charge.

4. Bill exceeding included quota
   **Given** customer has 100,000 monthly quota, 80,000 tokens used this month, overage rate $0.02 per 1K tokens
   **When** submitting 50,000 tokens
   **Then** bill shows: 20,000 from quota, 30,000 overage, $0.60 charge.

5. Successful return
   **Given** valid request
   **When** bill is calculated
   **Then** return HTTP 201 with bill details including: bill ID, customer ID, total tokens, tokens from quota, overage tokens, total charge, and calculation timestamp.

---

## Domain Concept Identification

### Existing Concepts (from codebase)

- **Customer**: Identity holder for billing; referenced by `customers` table with `id` (VARCHAR) as primary key — central entity that all billing revolves around
- **PricingPlan**: Defines billing parameters (`monthly_quota`, `overage_rate_per_1k`); stored in `pricing_plans` table — provides the rate card for billing calculations
- **CustomerSubscription**: Links a customer to a pricing plan with temporal validity (`effective_from`, `effective_to`); stored in `customer_subscriptions` table — determines which plan governs a customer's billing at any point in time
- **Bill**: Records a single usage submission and its calculated charges; stored in `bills` table with `prompt_tokens`, `completion_tokens`, `total_tokens`, `included_tokens_used`, `overage_tokens`, `total_charge`, `calculated_at` — the output artifact of this feature

### New Concepts Required

- **UsageRequest**: Input DTO representing a usage submission (customer ID, prompt tokens, completion tokens) — transient concept, not persisted, transforms into a Bill
- **BillResponse**: Output DTO representing the calculated bill returned to the client — maps from Bill entity with the fields specified in AC5
- **CurrentMonthUsage**: Derived concept representing aggregated token usage for a customer within the current calendar month — calculated on-demand from existing bills, not a separate entity

### Key Business Rules

- **Total Tokens Calculation**: `total_tokens = prompt_tokens + completion_tokens` — governs Bill
- **Quota-First Consumption**: Tokens are deducted from monthly quota before any overage is charged — governs Bill calculation
- **Remaining Quota Calculation**: `remaining_quota = monthly_quota - sum(included_tokens_used for current month's bills)` — governs Bill calculation, derived from PricingPlan and existing Bills
- **Included Tokens Allocation**: `included_tokens_used = min(total_tokens, remaining_quota)` — governs Bill
- **Overage Tokens Calculation**: `overage_tokens = total_tokens - included_tokens_used` — governs Bill
- **Charge Calculation**: `total_charge = (overage_tokens / 1000) × overage_rate_per_1k` — governs Bill, uses PricingPlan rate
- **Customer Validation**: Customer ID must reference an existing `customers` record — governs request validation
- **Token Non-Negativity**: Both `prompt_tokens` and `completion_tokens` must be ≥ 0 — governs request validation
- **Active Subscription Resolution**: Customer's billing uses the plan from their active subscription (where current date is within `effective_from` and `effective_to` range) — governs which PricingPlan is used

---

## Strategic Approach

### Solution Direction

Implement a standard Spring Boot REST endpoint following the existing project conventions:

1. **REST Controller** receives POST /api/usage with validation annotations
2. **Service Layer** orchestrates the billing calculation logic:
   - Resolve customer's active subscription → pricing plan
   - Aggregate current month's usage from existing bills
   - Calculate new bill using quota-first consumption rules
   - Persist bill and return response
3. **Repository Layer** provides data access via Spring Data JPA for Customer, Subscription, and Bill entities
4. **Entity Layer** maps to existing database schema using JPA annotations

The architecture follows a clean request → validation → business logic → persistence → response flow.

### Key Design Decisions

| Decision | Trade-offs | Recommendation |
|----------|------------|----------------|
| **Current month definition** | Calendar month (simple, aligned with common billing) vs. rolling 30-day window (more complex, potentially fairer) | Use calendar month (UTC timezone) — simpler, matches typical billing cycles, and aligns with "monthly quota" terminology |
| **Usage aggregation approach** | Query-time aggregation (always accurate, no stale data) vs. cached/materialized view (faster reads, stale risk) | Query-time aggregation — correctness over performance for this billing-critical operation; optimize later if needed |
| **Subscription resolution** | Fail if no active subscription vs. use default/free plan | Fail with 404-like error — explicit failure is safer for billing; prevents accidental free usage |
| **Concurrent submission handling** | Optimistic locking (simple, occasional retry) vs. pessimistic locking (consistent, potential bottleneck) | Defer to implementation phase — both approaches are valid; start simple without explicit locking and address if race conditions materialize |
| **Overage rate precision** | Round per-submission (simple) vs. accumulate fractional tokens (precise) | Use BigDecimal with HALF_UP rounding to 2 decimal places per submission — matches schema precision and keeps each bill self-contained |

### Alternatives Considered

- **Event-driven architecture** (emit usage event, async billing calculation): Rejected — over-engineered for synchronous billing requirement; the API must return calculated bill immediately
- **Pre-computed quota balance table**: Rejected — introduces data consistency challenges; query-time aggregation is simpler and correct
- **Customer-embedded subscription/plan**: Rejected — existing schema already normalizes these appropriately; no need to denormalize

---

## Risk & Gap Analysis

### Requirement Ambiguities

| Ambiguity | What needs clarification |
|-----------|-------------------------|
| **"Current month" definition** | Is it calendar month? Which timezone (server, customer, UTC)? Recommendation: Assume UTC calendar month. |
| **No active subscription scenario** | Requirement assumes customer has a subscription. What response when customer exists but has no active subscription? Recommendation: Return 422 with "No active subscription found". |
| **Multiple active subscriptions** | Schema allows overlapping `effective_from`/`effective_to` ranges. Which subscription takes precedence? Recommendation: Use most recently created subscription (`created_at` DESC) or fail if ambiguous. |
| **Overage rate = 0 handling** | Free plans may have $0 overage rate. Is overage still tracked even if charge is $0? Recommendation: Yes, track overage tokens even with zero charge. |
| **Subscription effective_to semantics** | Is `effective_to` inclusive or exclusive? NULL means indefinite? Recommendation: Inclusive end date; NULL means no end date (active indefinitely). |

### Edge Cases

| Scenario | Why it matters |
|----------|----------------|
| **First usage of the month** | Remaining quota equals full monthly quota; verify calculation handles zero prior usage |
| **Exactly exhausts quota** | Usage exactly matches remaining quota; verify 0 overage tokens and $0 charge |
| **Zero token submission** | Both prompt and completion tokens = 0; technically valid, should produce $0 bill |
| **Subscription starts mid-month** | Does new subscriber get full monthly quota or prorated? Recommendation: Full quota (simpler, favors customer) |
| **Subscription ends today** | If `effective_to` = today, is subscription still active? Recommendation: Yes, inclusive |
| **Very large token submission** | Integer overflow risk if tokens exceed INT_MAX; schema uses INTEGER — verify application-level bounds |
| **Fractional charge calculation** | `30,000 overage × $0.02/1K = $0.60` — verify no floating-point precision issues (use BigDecimal) |

### Technical Risks

| Risk | Potential Impact | Mitigation Direction |
|------|------------------|---------------------|
| **Concurrent submissions race condition** | Two simultaneous submissions may both calculate using same "current usage", causing over-allocation of quota | Consider database-level locking on quota calculation or optimistic retry; acceptable to defer if low concurrency expected |
| **Month boundary race condition** | Submission at 23:59:59.999 UTC on month-end may have aggregation race with next month's first submission | Use transaction isolation; accept minimal risk for this edge case |
| **Query performance degradation** | Aggregating all bills for current month may slow down as usage grows | Add composite index on `(customer_id, calculated_at)`; already exists in schema |
| **Timezone inconsistency** | Server timezone vs. database timezone mismatch could cause month calculation errors | Explicitly use UTC throughout; store `calculated_at` in UTC |

### Acceptance Criteria Coverage

| AC# | Description | Addressable? | Gaps/Notes |
|-----|-------------|--------------|------------|
| AC1 | Validate Customer ID exists → 404 "Customer not found" | Yes | Straightforward JPA lookup |
| AC2 | Validate token counts non-negative → 400 "Token count cannot be negative" | Yes | Use `@Min(0)` validation annotation |
| AC3 | Bill within quota → 30,000 from quota, 0 overage, $0.00 | Yes | Core calculation logic |
| AC4 | Bill exceeding quota → 20,000 from quota, 30,000 overage, $0.60 | Yes | Core calculation logic |
| AC5 | Return HTTP 201 with bill details | Yes | Bill ID, customer ID, total tokens, tokens from quota, overage tokens, total charge, timestamp |

**AC Coverage Summary**: All 5 ACs are addressable with the proposed approach. No gaps identified in explicit AC coverage.

**Implicit Requirements Not in ACs**:
- Active subscription required (not explicitly stated as AC, but implied by "customer's monthly quota")
- Response structure beyond the listed fields (e.g., prompt_tokens, completion_tokens separately?)
- Error response format/structure for 400/404 cases
