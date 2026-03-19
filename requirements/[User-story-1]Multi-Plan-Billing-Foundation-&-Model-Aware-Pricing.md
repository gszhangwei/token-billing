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
