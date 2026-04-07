## [STORY-001-002] Premium Plan Split-Rate Billing

### Background

With the multi-plan billing foundation established in User-story-3, we can now introduce the Premium plan — a fundamentally different billing model targeting customers who prefer pay-per-use without quota limits. Premium customers are billed separately for prompt tokens and completion tokens, each at model-specific rates. This reflects the reality that generating output (completion) is typically more computationally expensive than processing input (prompt), and different AI models have different cost profiles. The Premium plan is our first net-new billing strategy and validates that the routing mechanism introduced in User-story-3 works correctly for multiple plan types.

### Business Value

- **New Revenue Model**: Capture a market segment that prefers transparent per-token pricing over quota-based plans, opening a new monetization path
- **Cost-Reflective Billing**: Charge separately for prompt and completion tokens at different rates, accurately reflecting the underlying cost structure of each AI model
- **Platform Validation**: Prove that the billing routing mechanism supports multiple, fundamentally different billing strategies — confirming the architecture is ready for future plan types

### Dependencies and Assumptions

- **Prerequisites**: User-story-1 must be completed first — it introduces the `modelId` field, the plan-type routing mechanism, and the Standard plan billing strategy. The Premium strategy plugs into the same routing infrastructure.
- **Data assumptions**: Premium plan customers exist with plan type "Premium". Per-model rate configurations exist with separate prompt and completion rates (e.g., for "fast-model": prompt $0.01/1K, completion $0.02/1K; for "reasoning-model": prompt $0.03/1K, completion $0.06/1K).
- **Integration points**: Uses the same POST /api/usage endpoint enhanced in User-story-1
- **Business constraints**: Premium plan has no monthly quota — all tokens are billed. The distinction between prompt and completion tokens already exists in the request payload from the original billing story.

### Scope In

- Implement Premium plan billing strategy within the existing routing mechanism
- Calculate prompt token charges and completion token charges separately using model-specific rates
- Return both charge components (prompt charge, completion charge) in addition to total charge in the bill response
- Premium plan has no quota concept — all usage is billed directly

### Scope Out

- Multi-plan routing mechanism and `modelId` API changes
- Standard plan billing logic
- Enterprise plan and volume-based tiered discounts
- Subscription plan CRUD
- Invoice generation

### Acceptance Criteria (ACs)

#### AC1: Premium Plan — Fast Model Billing

**Given** a Premium plan customer. For "fast-model", the prompt rate is $0.01 per 1,000 tokens and the completion rate is $0.02 per 1,000 tokens.
**When** submitting 10,000 prompt tokens and 5,000 completion tokens for "fast-model"
**Then** the bill shows: prompt charge $0.10 (10,000 / 1,000 _ $0.01), completion charge $0.10 (5,000 / 1,000 _ $0.02), total charge $0.20. No quota fields are applicable.

#### AC2: Premium Plan — Reasoning Model Billing

**Given** a Premium plan customer. For "reasoning-model", the prompt rate is $0.03 per 1,000 tokens and the completion rate is $0.06 per 1,000 tokens.
**When** submitting 10,000 prompt tokens and 20,000 completion tokens for "reasoning-model"
**Then** the bill shows: prompt charge $0.30 (10,000 / 1,000 _ $0.03), completion charge $1.20 (20,000 / 1,000 _ $0.06), total charge $1.50.

#### AC3: Premium Plan — No Quota Deduction

**Given** a Premium plan customer, regardless of how much usage has been accumulated this month
**When** submitting any number of tokens
**Then** the bill always charges the full token amount at the applicable rates. There is no concept of "tokens from quota" or "overage tokens" — all tokens are billed directly.

#### AC4: Premium Plan — Zero Prompt or Completion Tokens

**Given** a Premium plan customer using "fast-model" (prompt $0.01/1K, completion $0.02/1K)
**When** submitting 0 prompt tokens and 10,000 completion tokens
**Then** the bill shows: prompt charge $0.00, completion charge $0.20, total charge $0.20. Zero-token submissions for one token type are valid and result in a $0.00 charge for that component.

#### AC5: Premium Plan Response Includes Split Charges

**Given** a valid Premium plan usage submission
**When** the bill is calculated successfully
**Then** the response (HTTP 201) includes all standard bill fields plus: prompt charge amount, completion charge amount, and the `modelId`. The total charge equals the sum of prompt and completion charges.

#### AC6: Existing Plan Types Unaffected

**Given** a Standard plan customer submits usage
**When** the system routes the calculation
**Then** the Standard plan billing behavior from User-story-3 is unchanged. Adding the Premium strategy does not alter Standard plan calculations.

#### Non-Functional Expectations

- The Premium billing calculation must complete fast enough for real-time API responses.
- The system must handle concurrent usage submissions from multiple Premium customers correctly, with no cross-customer data leakage.
