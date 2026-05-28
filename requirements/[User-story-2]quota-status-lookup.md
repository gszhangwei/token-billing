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
