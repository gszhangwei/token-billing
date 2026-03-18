## Background
As our LLM API platform scales, a single pricing model is no longer sufficient. We are transitioning to a multi-plan, model-aware billing system. We need to refactor our existing billing engine to support different subscription strategies, variable pricing based on the AI model invoked, and complex volume-based tiered discounts.

## Business Value
1. **Flexible Monetization**: Support diverse billing strategies (Standard, Premium, Enterprise) to capture different market segments.
2. **Model-Aware Pricing**: Charge different rates based on the specific AI model used.
3. **Architecture Scalability**: Implement an extensible design to isolate complex tiering logic and easily add future pricing models.

## Scope In
* Enhance the existing `POST /api/usage` endpoint.
* **New Request Field:** Add `modelId` (required, string, e.g., "fast-model", "reasoning-model").
* Introduce dynamic billing calculation based on three Plan Types:
    1. **Standard Plan (Legacy Refactor):** Has a monthly global quota. Overage rates now depend on the `modelId`.
    2. **Premium Plan:** No quota. Prompt and Completion tokens are billed separately, and rates vary by `modelId`.
    3. **Enterprise Plan (New Complex Logic):** Volume-based tiered discount. The price drops as the customer's total monthly usage crosses specific thresholds. Calculating this requires querying the customer's *accumulated usage for the current month* before applying the rates.
* Implement a routing mechanism (Strategy/Factory Pattern) to handle these distinct calculation formulas.

## Scope Out
* Subscription plan creation and assignment CRUD.
* Invoice generation.
* Changing plan status mid-billing cycle.

## Acceptance Criteria (ACs)
1. **Base Validations (Regression & New)**
   **Given** an invalid request (e.g., missing `modelId`, negative tokens)
   **When** backend validates request
   **Then** return HTTP 400 with appropriate error messages.

2. **Standard Plan with Model-Aware Overage**
   **Given** a "Standard" customer with a 100,000 monthly quota, 90,000 used so far. Overage for "fast-model" is $0.01/1K.
   **When** submitting 30,000 tokens for "fast-model"
   **Then** bill shows: 10,000 from quota, 20,000 overage, $0.20 charge.

3. **Premium Plan with Split Rates**
   **Given** a "Premium" customer. For "reasoning-model", Prompt is $0.03/1K, Completion is $0.06/1K.
   **When** submitting 10,000 prompt and 20,000 completion tokens for "reasoning-model"
   **Then** bill shows: 0 from quota, $0.30 prompt charge, $1.20 completion charge, total $1.50.

4. **Enterprise Plan with Cross-Tier Calculation (Complex)**
   **Given** an "Enterprise" customer using "fast-model". Tier 1 (0 to 50,000 tokens) is $0.02/1K. Tier 2 (above 50,000 tokens) is $0.01/1K. The customer has *already used 40,000 tokens* this month.
   **When** submitting 30,000 tokens (which pushes the total to 70,000)
   **Then** bill splits the usage: 10,000 tokens calculated at Tier 1 rate ($0.20), and 20,000 tokens calculated at Tier 2 rate ($0.20), for a total charge of $0.40.