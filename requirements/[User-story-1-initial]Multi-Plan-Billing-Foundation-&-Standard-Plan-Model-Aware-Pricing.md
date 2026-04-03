## [STORY-001-001] Multi-Plan Billing Foundation & Standard Plan Model-Aware Pricing

### Background

As our LLM API platform scales, a single flat-rate pricing model is no longer sufficient. Different AI models (e.g., "fast-model" vs. "reasoning-model") have vastly different operational costs, and our billing must reflect that. This story lays the foundation for multi-plan billing by introducing model identification into the usage submission flow and refactoring the existing Standard plan to charge model-specific overage rates. It also establishes the routing mechanism that directs billing calculations to the correct plan strategy, enabling future plan types (Premium, Enterprise) to be added cleanly.

### Business Value

- **Model-Aware Monetization**: Charge customers accurately based on the actual cost of the AI model they use, rather than applying a single flat overage rate
- **Foundation for Multi-Plan Growth**: Establish the billing routing infrastructure that supports diverse subscription strategies, enabling the business to launch Premium and Enterprise plans
- **Backward Compatibility**: Existing Standard plan customers continue to receive quota-based billing, now enhanced with model-specific overage rates

### Dependencies and Assumptions

- **Prerequisites**: The original token-usage billing (POST /api/usage) is already implemented and working (covered by the foundation billing story)
- **Data assumptions**: Customers already exist with assigned plan types. Model-specific overage rates are configured for each model (e.g., "fast-model" at $0.01/1K, "reasoning-model" at $0.03/1K). Standard plan customers have monthly quotas.
- **Integration points**: The existing POST /api/usage endpoint is enhanced (not replaced)
- **Business constraints**: Existing billing behavior for Standard plan customers must not break — the only change is that overage rates now vary by model instead of using a single flat rate

### Scope In

- Add `modelId` as a new required field to the POST /api/usage request
- Validate `modelId` presence and that it refers to a known, supported model
- Implement a routing mechanism to direct billing calculations based on the customer's plan type
- Refactor Standard plan billing: overage tokens are charged at the model-specific rate instead of a single global rate
- Maintain all existing validations (customer existence, non-negative tokens)

### Scope Out

- Premium plan billing logic
- Enterprise plan and volume-based tiered discounts
- Subscription plan CRUD (creating/assigning plans to customers)
- Invoice generation
- Model CRUD operations (adding/removing supported models)

### Acceptance Criteria (ACs)

#### AC1: New modelId Validation

**Given** a usage submission request is missing the `modelId` field
**When** the system receives the request
**Then** the system rejects it with HTTP 400 and a message indicating that model ID is required.

#### AC2: Unknown Model Rejection

**Given** a usage submission includes a `modelId` that does not match any configured model (e.g., "unknown-model")
**When** the system receives the request
**Then** the system rejects it with HTTP 400 and a message indicating the model is not supported.

#### AC3: Existing Validations Still Apply

**Given** a request with a valid `modelId` but an invalid customer ID or negative token counts
**When** the system validates the request
**Then** the system returns the same errors as before (HTTP 404 for missing customer, HTTP 400 for negative tokens). Adding `modelId` does not change existing validation behavior.

#### AC4: Standard Plan — Usage Within Quota

**Given** a Standard plan customer with a 100,000 monthly quota, 60,000 tokens already used this month
**When** submitting 30,000 tokens for "fast-model"
**Then** the bill shows: 30,000 tokens consumed from quota, 0 overage tokens, $0.00 total charge. The model is recorded but does not affect the bill since no overage occurred.

#### AC5: Standard Plan — Model-Aware Overage

**Given** a Standard plan customer with a 100,000 monthly quota, 90,000 tokens already used this month. The overage rate for "fast-model" is $0.01 per 1,000 tokens.
**When** submitting 30,000 tokens for "fast-model"
**Then** the bill shows: 10,000 tokens from quota, 20,000 overage tokens, $0.20 total charge (20,000 / 1,000  $0.01).

#### AC6: Standard Plan — Different Model, Different Rate

**Given** the same Standard plan customer (100,000 quota, 90,000 used). The overage rate for "reasoning-model" is $0.03 per 1,000 tokens.
**When** submitting 30,000 tokens for "reasoning-model"
**Then** the bill shows: 10,000 tokens from quota, 20,000 overage tokens, $0.60 total charge (20,000 / 1,000  $0.03). The rate differs from AC5 because a different model was used.

#### AC7: Successful Response Includes Model Information

**Given** a valid usage submission with `modelId` = "fast-model"
**When** the bill is calculated successfully
**Then** the response (HTTP 201) includes all existing bill fields (bill ID, customer ID, total tokens, tokens from quota, overage tokens, total charge, timestamp) plus the `modelId` used for the calculation.

#### Non-Functional Expectations

- The billing calculation must remain fast enough for real-time API responses, even with the added plan-routing and model-rate-lookup steps.
- The routing mechanism must be extensible — adding a new plan type in the future should not require changes to existing plan strategies.

