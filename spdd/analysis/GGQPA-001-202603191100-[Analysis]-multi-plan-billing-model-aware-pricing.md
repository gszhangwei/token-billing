# SPDD Analysis: Multi-Plan Billing Foundation & Model-Aware Pricing

## Original Business Requirement

## Background

As our LLM API platform scales, a single pricing model is no longer sufficient. We need to refactor our existing billing engine to support different subscription strategies and variable pricing based on the AI model invoked, laying the foundation for future complex billing plans.

## Business Value

1. **Flexible Monetization**: Support diverse billing strategies (Standard, Premium) to capture different market segments.
2. **Model-Aware Pricing**: Charge different rates based on the specific AI model used.
3. **Architecture Scalability**: Implement an extensible design (e.g., Strategy Pattern) to isolate calculation logic and easily add future pricing models.

## Scope In

- Enhance the existing POST /api/usage endpoint.
- **New Request Field:** Add modelId (required, string, e.g., "fast-model", "reasoning-model").
- Implement a routing mechanism (Strategy/Factory Pattern) to handle distinct calculation formulas.
- Implement two initial Plan Types:
  1. **Standard Plan (Legacy Refactor):** Has a monthly global quota. Overage rates now depend on the modelId.
  2. **Premium Plan (New):** No quota. Prompt and Completion tokens are billed separately, and rates vary by modelId.

## Scope Out

- Complex tiered/volume-based discount logic (Deferred to Phase 2).
- Subscription plan creation and assignment CRUD.
- Invoice generation.

## Acceptance Criteria (ACs)

1. **Base Validations (Regression & New)**
   **Given** an invalid request (e.g., missing `modelId`, negative tokens)
   **When** backend validates request
   **Then** return HTTP 400 with appropriate error messages.
2. **Standard Plan with Model-Aware Overage**
   **Given** a "Standard" customer with a 100,000 monthly quota, 90,000 used so far. Overage for "fast-model" is `$0.01/1K`.
   **When** submitting 30,000 tokens for "fast-model"
   **Then** bill shows: 10,000 from quota, 20,000 overage, $0.20 charge.
3. **Premium Plan with Split Rates**
   **Given** a "Premium" customer. For "reasoning-model", Prompt is `$0.03/1K`, Completion is `$0.06/1K`.
   **When** submitting 10,000 prompt and 20,000 completion tokens for "reasoning-model"
   **Then** bill shows: 0 from quota, `$0.30` prompt charge, `$1.20` completion charge, total `$1.50`.

---

## Domain Concept Identification

### Existing Concepts (from codebase)

- **Customer**: Identity holder for billing; `customers` table with `id` (VARCHAR) as PK — unchanged, remains the billing anchor entity
- **PricingPlan**: Currently defines `monthly_quota` and single `overage_rate_per_1k` in `pricing_plans` table — **needs refactoring** to support plan types and remove model-agnostic rate
- **CustomerSubscription**: Links customer to pricing plan with temporal validity (`effective_from`, `effective_to`) — unchanged, continues to determine which plan governs billing
- **Bill**: Records usage and calculated charges in `bills` table — **needs extension** to store `model_id` and potentially split charge breakdown
- **UsageRequest**: Input DTO for usage submission — **needs extension** to include required `modelId` field
- **BillResponse**: Output DTO for calculated bill — **needs extension** to include model-specific charge breakdown

### New Concepts Required

- **PlanType**: Discriminator that identifies the billing strategy (e.g., "STANDARD", "PREMIUM") — attached to PricingPlan, determines which calculation strategy to apply
- **ModelPricing**: Rate configuration per AI model per plan — stores model-specific rates (overage rate for Standard, prompt/completion rates for Premium)
- **BillingStrategy**: Calculation logic encapsulation (Strategy Pattern) — polymorphic billing calculators selected based on PlanType
  - **StandardBillingStrategy**: Quota-first consumption with model-aware overage rates
  - **PremiumBillingStrategy**: No quota, separate prompt/completion charges with model-aware rates
- **BillingStrategyFactory**: Factory to resolve the appropriate BillingStrategy based on PlanType

### Key Business Rules

- **Model ID Required**: Every usage submission must specify which AI model was invoked — governs UsageRequest validation
- **Plan Type Determines Strategy**: The PlanType field on PricingPlan dictates which calculation formula to use — governs strategy selection
- **Standard Plan Rules**:
  - Global monthly quota still applies (unchanged from legacy)
  - `remaining_quota = monthly_quota - sum(included_tokens_used for current month)`
  - `included_tokens_used = min(total_tokens, max(remaining_quota, 0))`
  - `overage_tokens = total_tokens - included_tokens_used`
  - **Model-aware overage**: `overage_charge = (overage_tokens / 1000) × model_specific_overage_rate`
- **Premium Plan Rules**:
  - No quota concept; `monthly_quota = NULL` or 0, `included_tokens_used = 0`
  - Split billing: prompt and completion tokens charged separately
  - `prompt_charge = (prompt_tokens / 1000) × model_prompt_rate`
  - `completion_charge = (completion_tokens / 1000) × model_completion_rate`
  - `total_charge = prompt_charge + completion_charge`
- **Model Pricing Resolution**: Given a (plan, modelId) pair, resolve the applicable rates — fail if no pricing configured for the requested model
- **Backward Compatibility**: Existing plans must continue working; Standard plan refactors legacy behavior without breaking existing customers

---

## Strategic Approach

### Solution Direction

Refactor the billing engine to support polymorphic calculation strategies, following the existing project conventions:

1. **Schema Evolution**: Add `plan_type` to `pricing_plans`, create `model_pricing` table for model-specific rates, add `model_id` to `bills` and potentially charge breakdown fields
2. **Strategy Pattern Implementation**: Introduce `BillingStrategy` interface with `StandardBillingStrategy` and `PremiumBillingStrategy` implementations
3. **Factory Pattern**: Use a `BillingStrategyFactory` (or Spring bean lookup by PlanType) to resolve the appropriate strategy at runtime
4. **Service Refactor**: `BillingServiceImpl.calculateBill()` delegates to the resolved strategy instead of hardcoded logic
5. **Domain Model Update**: `Bill.create()` becomes strategy-specific; each strategy produces its own Bill variant or uses a unified Bill with optional fields
6. **DTO Extension**: Add `modelId` to `UsageRequest`; extend `BillResponse` with model info and charge breakdown

### Key Design Decisions

| Decision | Trade-offs | Recommendation |
|----------|------------|----------------|
| **Where to store PlanType** | In PricingPlan table (denormalized, simple lookup) vs. separate PlanType reference table (normalized, more flexible) | Add `plan_type VARCHAR` column to `pricing_plans` — simpler, sufficient for STANDARD/PREMIUM enum; normalize later if more metadata needed |
| **Model pricing storage** | Embed rates in PricingPlan (simple but rigid) vs. separate `model_pricing` table (normalized, model per plan flexibility) | Create `model_pricing` table with FK to `pricing_plans` and model_id — supports multiple models per plan with different rates |
| **Strategy resolution mechanism** | Enum-based switch (simple, compile-time safe) vs. Spring bean lookup by name (flexible, injectable) | Use Spring bean lookup with `@Qualifier` or bean name by PlanType — aligns with Spring idioms, allows strategies to have injected dependencies |
| **Premium Plan quota handling** | Set `monthly_quota = 0` (reuses existing column) vs. `monthly_quota = NULL` (semantically distinct) | Use `monthly_quota = 0` for Premium — simpler, avoids null handling complexity; Standard always has `monthly_quota > 0` |
| **Bill charge breakdown storage** | Single `total_charge` only (current) vs. add `prompt_charge`, `completion_charge` columns | Add `prompt_charge` and `completion_charge` nullable columns to `bills` — enables Premium billing breakdown and audit trail |
| **Model validation** | Fail if model not in pricing table vs. use default rates | Fail with HTTP 400 "Unknown model" — explicit failure prevents billing errors; models must be explicitly configured |

### Alternatives Considered

- **Inheritance-based Plan entities (StandardPlan, PremiumPlan)**: Rejected — JPA single-table inheritance adds complexity; discriminator column (`plan_type`) with strategy pattern achieves polymorphism without entity hierarchy
- **Configuration-driven formula evaluation (e.g., expression language)**: Rejected — over-engineered for two well-defined calculation types; Strategy pattern is cleaner and more maintainable
- **Separate endpoints per plan type**: Rejected — violates RESTful resource design; single `/api/usage` endpoint with internal routing is cleaner
- **Storing rates as JSON blob**: Rejected — loses query capability and type safety; normalized `model_pricing` table is more robust

---

## Risk & Gap Analysis

### Requirement Ambiguities

| Ambiguity | What needs clarification |
|-----------|-------------------------|
| **Default model pricing** | What happens if a customer submits usage for a model without configured pricing? Recommendation: Return HTTP 400 "Pricing not configured for model: {modelId}". |
| **Standard Plan model not configured** | Should Standard plan have a fallback overage rate if specific model pricing is missing? Recommendation: No fallback; require explicit model pricing configuration. |
| **Premium Plan `included_tokens_used` semantics** | Should the response show `included_tokens_used = 0` explicitly, or omit the field? Recommendation: Always include with value 0 for Premium — consistent response schema. |
| **Charge breakdown in response** | AC3 mentions "$0.30 prompt charge, $1.20 completion charge" — should response have separate fields or just a breakdown description? Recommendation: Add `promptCharge`, `completionCharge` fields to BillResponse. |
| **modelId validation rules** | What format/length constraints apply to modelId? Recommendation: Non-empty string, max 50 characters, following slug format (lowercase, hyphens). |
| **Bill table backward compatibility** | Existing bills have no `model_id`. Should we add a NOT NULL constraint? Recommendation: Add `model_id` as nullable initially; existing bills remain valid with NULL. |

### Edge Cases

| Scenario | Why it matters |
|----------|----------------|
| **Model not configured for plan** | Customer's plan doesn't have pricing for the submitted modelId — verify clear error message |
| **Standard Plan with zero remaining quota** | All tokens become overage; verify full overage calculation with model-specific rate |
| **Premium Plan with zero tokens** | 0 prompt and 0 completion tokens — should produce $0.00 total, valid bill |
| **Premium Plan one token type only** | e.g., 0 prompt, 1000 completion — verify correct single-charge calculation |
| **Very high rates** | Verify no overflow; BigDecimal handles large charges correctly |
| **Multiple models in same plan** | Verify model_pricing lookup correctly isolates rates per model |
| **Legacy data migration** | Existing bills without model_id; existing plans without plan_type — verify migration strategy |

### Technical Risks

| Risk | Potential Impact | Mitigation Direction |
|------|------------------|---------------------|
| **Schema migration complexity** | Adding columns and new table requires careful migration; existing seed data needs plan_type | Create V2 migration that: (1) adds plan_type with default 'STANDARD', (2) creates model_pricing table, (3) adds model_id to bills as nullable, (4) migrates existing pricing to model_pricing |
| **Strategy selection performance** | Bean lookup per request could add overhead | Strategies are singleton beans; lookup is O(1) HashMap; negligible impact |
| **Breaking existing tests** | Existing tests assume single pricing model; many will fail | Update tests incrementally; ensure regression tests still pass with Standard plan behavior |
| **Model pricing cache staleness** | If pricing loaded once at startup, changes require restart | For MVP, accept restart requirement; add cache invalidation later if needed |
| **Concurrency on quota calculation** | Same risk as before; now with additional model dimension | Unchanged mitigation; quota is still global per customer, not per model |

### Acceptance Criteria Coverage

| AC# | Description | Addressable? | Gaps/Notes |
|-----|-------------|--------------|------------|
| AC1 | Validate invalid requests (missing modelId, negative tokens) → 400 | Yes | Add `@NotNull` for modelId; existing validation handles negative tokens |
| AC2 | Standard Plan: 100K quota, 90K used, 30K for "fast-model" @ $0.01/1K → 10K from quota, 20K overage, $0.20 | Yes | StandardBillingStrategy with model-specific overage rate |
| AC3 | Premium Plan: "reasoning-model" prompt $0.03/1K, completion $0.06/1K → 10K×$0.03 + 20K×$0.06 = $0.30 + $1.20 = $1.50 | Yes | PremiumBillingStrategy with split rates |

**AC Coverage Summary**: All 3 ACs are addressable with the proposed Strategy Pattern approach.

**Implicit Requirements Not in ACs**:
- Response structure needs `modelId` and potentially charge breakdown fields
- Error response for unknown model scenario
- Migration path for existing data (plan_type, model_pricing seed data)
- Existing functionality regression (Standard plan customers without explicit model pricing config)
