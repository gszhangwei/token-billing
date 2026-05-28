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

## Domain Concept Identification

### Existing Concepts (from codebase)

The database schema (Flyway `V1__Create_tables.sql`) and seed data already model the full domain. No application-layer code exists yet beyond the Spring Boot bootstrap class.

- **Customer**: Identifies who consumed tokens. Referenced by subscriptions and bills. Seed IDs include `CUST-001`, `CUST-002`, `CUST-003`.
- **PricingPlan**: Defines `monthly_quota` and `overage_rate_per_1k`. Reusable across customers. Seed plans from Free (10K) through Enterprise (2M).
- **CustomerSubscription**: Links a customer to a plan with `effective_from` / optional `effective_to`. Determines which quota and rate apply at billing time.
- **Bill**: Persists each usage submission and calculated outcome (token breakdown, included vs overage, charge, timestamp). Also serves as the ledger for deriving current-month usage.

Relationships: Customer → Subscription → PricingPlan; each usage submission creates a Bill for that Customer.

### New Concepts Required

All domain tables exist; the gap is **application behavior**, not new entities:

- **Usage submission flow**: Accept token counts, orchestrate validation, calculation, persistence, and response.
- **Billing calculation**: Apply quota-first consumption and overage pricing for the active plan.
- **Current-month usage aggregation**: Derive prior consumption from existing bill rows (no separate usage table; no automated reset job per scope).

No new database tables are required for the in-scope story unless the REASONS phase chooses supporting types (DTOs, exceptions) in code only.

### Key Business Rules

- **Total tokens** = prompt tokens + completion tokens (explicit in README; implied by AC field breakdown).
- **Included tokens first**: Consume remaining monthly quota before any overage.
- **Overage charge** = (overage tokens ÷ 1000) × plan's overage rate per 1K.
- **Monthly quota** comes from the customer's active subscription's pricing plan.
- **Current-month usage** = sum of prior bills' total tokens for that customer in the current calendar month (implicit; required to implement AC3–AC4; quota reset automation is explicitly out of scope).
- **Active subscription**: Must resolve which plan applies on the billing date (implicit when customer has subscription rows with effective dates).
- **Exact error messages** (contractual per ACs): `"Customer not found"` (404), `"Token count cannot be negative"` (400).
- **Successful submission** returns 201 with bill identity and all calculated fields.

## Strategic Approach

### Solution Direction

Implement a single REST endpoint (`POST /api/usage`) in a layered Spring Boot service:

1. Validate request (customer exists, non-negative token counts).
2. Resolve customer → active subscription → pricing plan.
3. Compute remaining quota from plan limit minus current-month billed tokens.
4. Apply quota-first split and overage pricing.
5. Persist a new bill row and return 201 with bill details.

High-level flow: **HTTP request → controller → billing service → JPA repositories → PostgreSQL**.

The project is **greenfield at the code layer**: dependencies (Web, JPA, Validation, Flyway) and schema are in place; controllers, services, repositories, DTOs, and exception handling remain to be built under `org.tw.token_billing`.

### Key Design Decisions

| Decision | Trade-offs | Recommendation |
|----------|------------|----------------|
| Derive monthly usage from `bills` table | Simple and matches schema; couples usage tracking to bill persistence. Alternative: separate usage_events table. | Use `bills` aggregation — no schema change, aligns with scope out (no new query APIs). |
| Single active subscription lookup | Assumes at most one active plan per customer; overlapping subscriptions need a rule. | Pick subscription effective on billing date; if none, treat as error (404 or 422 — not specified in story). |
| Persist-then-respond | Each POST creates a bill row; idempotency not required by story. | Always insert bill on success; enables AC3/AC4 scenarios via seeded prior bills in tests. |
| Validation split: Bean Validation + domain checks | Format/range in DTO; customer existence in service. | DTO `@Min(0)` for tokens; service/repository for customer lookup with exact 404 message. |
| Layering | More files vs monolithic controller. | Controller / Service / Repository / DTO / Exception handler — matches project conventions rule and Spring norms. |

### Alternatives Considered

- **Separate usage ledger table**: Rejected — `bills` already stores `total_tokens` and `calculated_at`; adding a table is out of scope and duplicates data.
- **In-memory quota tracking**: Rejected — not durable; contradicts need for current-month usage across requests.
- **Implement GET endpoints for bills**: Rejected — explicitly scope out.

## Risk & Gap Analysis

### Requirement Ambiguities

| Ambiguity | Notes |
|-----------|-------|
| No active subscription | Story does not say what happens if customer exists but has no active subscription. Recommend 404 or domain-specific error; must be decided in REASONS Safeguards. |
| Multiple overlapping subscriptions | Seed data has one per customer; real overlap rule undefined. Recommend "single active subscription on billing date." |
| Missing required fields | AC covers negative tokens but not null/missing `customerId` or token fields. Recommend 400 with validation message (exact text not specified). |
| JSON field naming | Story lists field names in prose, not JSON keys. Recommend camelCase (`customerId`, `promptTokens`, `completionTokens`) per Spring convention. |
| Timezone for "current month" | Not specified. Recommend server default timezone or UTC — document in Safeguards. |
| Zero-token submission | Not in ACs; mathematically valid (0 quota consumed, $0 charge). Allow unless product says otherwise. |

### Edge Cases

| Scenario | Why it matters |
|----------|----------------|
| Quota exactly exhausted | Submission uses last tokens at $0 overage; next token triggers overage. |
| Submission larger than remaining quota | Core AC4 path; partial included + partial overage. |
| First bill of the month | Current-month usage = 0; full quota available. |
| Concurrent submissions same customer | Could over-consume quota without locking; acceptable for MVP but note race risk. |
| Decimal charge rounding | `(30000/1000)*0.02 = 0.60` — use DECIMAL arithmetic, consistent scale in DB. |
| Customer exists but subscription `effective_from` in future | Active plan resolution fails; related to "no subscription" ambiguity. |

### Technical Risks

| Risk | Impact | Mitigation direction |
|------|--------|---------------------|
| Greenfield — no patterns to copy | Inconsistent structure | Follow `.cursor/rules/spring-boot-conventions.mdc` and REASONS Norms. |
| JPA `ddl-auto: validate` | Entity/schema mismatch fails startup | Map entities exactly to V1 columns; no Hibernate DDL changes. |
| Test isolation for AC3/AC4 | Need 60K/80K prior usage in month | Integration tests insert prior bills or use test SQL fixtures. |
| Flyway seed data in dev | AC manual testing needs known customers | Use `CUST-001` / PLAN-STARTER (100K quota, $0.02/1K) matching AC examples. |

### Acceptance Criteria Coverage

| AC# | Description | Addressable? | Gaps/Notes |
|-----|-------------|--------------|------------|
| 1 | Customer not found → 404, exact message | Yes | Straightforward repository lookup. |
| 2 | Negative tokens → 400, exact message | Yes | Bean Validation or manual check; message must match exactly. |
| 3 | Within quota billing | Yes | Requires current-month usage derivation; tests need prior bill seeding. |
| 4 | Exceeding quota with $0.60 charge | Yes | Depends on correct quota math and DECIMAL rate from plan. |
| 5 | 201 with full bill payload | Yes | Response field names to be fixed in REASONS (camelCase JSON recommended). |

All five ACs are addressable with the recommended approach. Open decisions (no subscription, missing fields, timezone) should be resolved in the REASONS Canvas Safeguards section before `/spdd-generate`.
