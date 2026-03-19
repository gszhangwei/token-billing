# Test Scenarios for Multi-Plan Billing Foundation & Model-Aware Pricing

> **Note**: This file contains only NEW test scenarios that don't exist in the current codebase.
> Existing tests have been analyzed and excluded to avoid duplication.

---

## 1. UsageController Test Scenarios (New Only)

### Update `UsageControllerTest` class
Add the following new test scenarios to the existing `UsageControllerTest` class.

#### should_return_201_with_charge_breakdown_when_submit_usage_given_valid_premium_plan_request
- Description: Submit valid usage for a Premium plan customer returns 201 with prompt/completion charge breakdown
- Input: POST `/api/usage` with `{"customerId": "CUST-PREMIUM", "modelId": "reasoning-model", "promptTokens": 10000, "completionTokens": 20000}`
- Expected Output: HTTP 201 with BillResponse including promptCharge and completionCharge
- Verification Points:
    - HTTP status is 201 Created
    - Response contains `promptCharge` (not null)
    - Response contains `completionCharge` (not null)
    - Response `includedTokensUsed` equals 0
    - Response `overageTokens` equals 0

#### should_return_400_when_submit_usage_given_missing_model_id
- Description: Missing modelId returns 400 Bad Request with validation error
- Input: POST `/api/usage` with `{"customerId": "CUST-001", "promptTokens": 1000, "completionTokens": 500}`
- Expected Output: HTTP 400 with error message "Model ID is required"
- Verification Points:
    - HTTP status is 400 Bad Request
    - Response contains error code "BAD_REQUEST"
    - Response message contains "Model ID is required"

#### should_return_400_when_submit_usage_given_unknown_model_id
- Description: Unknown modelId returns 400 Bad Request with pricing not configured message
- Input: POST `/api/usage` with `{"customerId": "CUST-001", "modelId": "unknown-model", "promptTokens": 1000, "completionTokens": 500}`
- Expected Output: HTTP 400 with error message "Pricing not configured for model: unknown-model"
- Verification Points:
    - HTTP status is 400 Bad Request
    - Response message contains "Pricing not configured for model"
    - Mock `BillingService` to throw `ModelPricingNotFoundException`

---

## 2. BillingServiceImpl Test Scenarios (New Only)

### Update `BillingServiceImplTest` class
Add the following new test scenarios to the existing `BillingServiceImplTest` class.

#### should_return_bill_with_split_charges_when_calculate_bill_given_premium_plan_usage
- Description: Premium plan usage returns bill with separate prompt and completion charges
- Input: UsageRequest with customerId="CUST-PREMIUM", modelId="reasoning-model", promptTokens=10000, completionTokens=20000
- Expected Output: Bill with promptCharge=$0.30, completionCharge=$1.20, totalCharge=$1.50
- Verification Points:
    - Bill includedTokensUsed equals 0
    - Bill overageTokens equals 0
    - Bill promptCharge equals $0.30 (10000/1000 × $0.03)
    - Bill completionCharge equals $1.20 (20000/1000 × $0.06)
    - Bill totalCharge equals $1.50
    - BillingStrategyFactory.getStrategy() called with PlanType.PREMIUM
- Setup:
    - Create PricingPlan with planType=PREMIUM, monthlyQuota=0
    - Create ModelPricing with promptRatePer1k=$0.03, completionRatePer1k=$0.06
    - Mock BillingStrategyFactory to return PremiumBillingStrategy

#### should_throw_model_pricing_not_found_exception_when_calculate_bill_given_unknown_model
- Description: Unknown model throws ModelPricingNotFoundException
- Input: UsageRequest with modelId="unknown-model" for valid customer with subscription
- Expected Output: ModelPricingNotFoundException thrown
- Verification Points:
    - ModelPricingNotFoundException is thrown
    - Exception message contains "unknown-model"
    - ModelPricingRepository.findByPlanIdAndModelId() returns empty
    - BillRepository.save() NOT called

#### should_return_zero_remaining_quota_when_calculate_bill_given_premium_plan
- Description: Premium plan always has zero remaining quota (no quota concept)
- Input: UsageRequest for Premium plan customer
- Expected Output: BillingContext built with remainingQuota=0
- Verification Points:
    - BillRepository.sumIncludedTokensUsedForMonth() NOT called for Premium plans (monthlyQuota=0)
    - Bill is calculated using PremiumBillingStrategy
- Setup:
    - Create PricingPlan with planType=PREMIUM, monthlyQuota=0

---

## 3. StandardBillingStrategy Test Scenarios (All New)

### Create `StandardBillingStrategyTest` class
1. Create `StandardBillingStrategyTest` class in `service/strategy/` package
2. Instantiate `StandardBillingStrategy` directly (no mocks needed - stateless)
3. Create test scenarios based on the prompts below
4. Generate test code for each test scenario

#### should_return_standard_when_supported_plan_type_given_strategy_instance
- Description: supportedPlanType() returns PlanType.STANDARD
- Input: StandardBillingStrategy instance
- Expected Output: PlanType.STANDARD
- Verification Points:
    - supportedPlanType() returns PlanType.STANDARD

#### should_return_bill_with_all_tokens_included_when_calculate_given_usage_within_quota
- Description: Usage within quota uses all tokens from quota, zero charge
- Input: BillingContext with promptTokens=1000, completionTokens=500, remainingQuota=10000, modelPricing with overageRatePer1k=$0.02
- Expected Output: Bill with includedTokensUsed=1500, overageTokens=0, totalCharge=0
- Verification Points:
    - Bill.includedTokensUsed equals 1500
    - Bill.overageTokens equals 0
    - Bill.totalCharge equals BigDecimal.ZERO
    - Bill.modelId equals context.modelId

#### should_return_bill_with_overage_when_calculate_given_usage_exceeds_quota
- Description: Usage exceeding quota calculates correct overage charge
- Input: BillingContext with promptTokens=8000, completionTokens=5000, remainingQuota=10000, modelPricing with overageRatePer1k=$0.02
- Expected Output: Bill with includedTokensUsed=10000, overageTokens=3000, totalCharge=$0.06
- Verification Points:
    - Bill.includedTokensUsed equals 10000
    - Bill.overageTokens equals 3000
    - Bill.totalCharge equals $0.06 (3000/1000 × $0.02)

#### should_return_bill_with_all_overage_when_calculate_given_zero_remaining_quota
- Description: Zero remaining quota treats all tokens as overage
- Input: BillingContext with promptTokens=1000, completionTokens=500, remainingQuota=0, overageRatePer1k=$0.02
- Expected Output: Bill with includedTokensUsed=0, overageTokens=1500, totalCharge=$0.03
- Verification Points:
    - Bill.includedTokensUsed equals 0
    - Bill.overageTokens equals 1500
    - Bill.totalCharge equals $0.03

#### should_return_bill_with_null_prompt_completion_charges_when_calculate_given_standard_plan
- Description: Standard plan bills have null promptCharge and completionCharge
- Input: Any valid BillingContext for Standard plan
- Expected Output: Bill with promptCharge=null, completionCharge=null
- Verification Points:
    - Bill.promptCharge is null
    - Bill.completionCharge is null

---

## 4. PremiumBillingStrategy Test Scenarios (All New)

### Create `PremiumBillingStrategyTest` class
1. Create `PremiumBillingStrategyTest` class in `service/strategy/` package
2. Instantiate `PremiumBillingStrategy` directly (no mocks needed - stateless)
3. Create test scenarios based on the prompts below
4. Generate test code for each test scenario

#### should_return_premium_when_supported_plan_type_given_strategy_instance
- Description: supportedPlanType() returns PlanType.PREMIUM
- Input: PremiumBillingStrategy instance
- Expected Output: PlanType.PREMIUM
- Verification Points:
    - supportedPlanType() returns PlanType.PREMIUM

#### should_return_bill_with_split_charges_when_calculate_given_valid_context
- Description: Calculate separate prompt and completion charges
- Input: BillingContext with promptTokens=10000, completionTokens=20000, modelPricing with promptRatePer1k=$0.03, completionRatePer1k=$0.06
- Expected Output: Bill with promptCharge=$0.30, completionCharge=$1.20, totalCharge=$1.50
- Verification Points:
    - Bill.promptCharge equals $0.30
    - Bill.completionCharge equals $1.20
    - Bill.totalCharge equals $1.50

#### should_return_bill_with_zero_included_tokens_when_calculate_given_premium_plan
- Description: Premium plan has no quota concept - includedTokensUsed always 0
- Input: Any valid BillingContext for Premium plan
- Expected Output: Bill with includedTokensUsed=0, overageTokens=0
- Verification Points:
    - Bill.includedTokensUsed equals 0
    - Bill.overageTokens equals 0

#### should_return_bill_with_zero_charges_when_calculate_given_zero_tokens
- Description: Zero tokens results in zero charges
- Input: BillingContext with promptTokens=0, completionTokens=0
- Expected Output: Bill with promptCharge=0, completionCharge=0, totalCharge=0
- Verification Points:
    - Bill.promptCharge equals BigDecimal.ZERO
    - Bill.completionCharge equals BigDecimal.ZERO
    - Bill.totalCharge equals BigDecimal.ZERO

#### should_return_bill_with_only_prompt_charge_when_calculate_given_zero_completion_tokens
- Description: Only prompt tokens generates only prompt charge
- Input: BillingContext with promptTokens=10000, completionTokens=0, promptRatePer1k=$0.03
- Expected Output: Bill with promptCharge=$0.30, completionCharge=0, totalCharge=$0.30
- Verification Points:
    - Bill.promptCharge equals $0.30
    - Bill.completionCharge equals BigDecimal.ZERO
    - Bill.totalCharge equals $0.30

#### should_return_bill_with_only_completion_charge_when_calculate_given_zero_prompt_tokens
- Description: Only completion tokens generates only completion charge
- Input: BillingContext with promptTokens=0, completionTokens=20000, completionRatePer1k=$0.06
- Expected Output: Bill with promptCharge=0, completionCharge=$1.20, totalCharge=$1.20
- Verification Points:
    - Bill.promptCharge equals BigDecimal.ZERO
    - Bill.completionCharge equals $1.20
    - Bill.totalCharge equals $1.20

---

## 5. BillingStrategyFactory Test Scenarios (All New)

### Create `BillingStrategyFactoryTest` class
1. Create `BillingStrategyFactoryTest` class in `service/strategy/` package
2. Create real strategy instances for testing
3. Create test scenarios based on the prompts below
4. Generate test code for each test scenario

#### should_return_standard_strategy_when_get_strategy_given_standard_plan_type
- Description: STANDARD plan type returns StandardBillingStrategy
- Input: PlanType.STANDARD
- Expected Output: StandardBillingStrategy instance
- Verification Points:
    - Returned strategy instanceof StandardBillingStrategy
    - strategy.supportedPlanType() returns PlanType.STANDARD

#### should_return_premium_strategy_when_get_strategy_given_premium_plan_type
- Description: PREMIUM plan type returns PremiumBillingStrategy
- Input: PlanType.PREMIUM
- Expected Output: PremiumBillingStrategy instance
- Verification Points:
    - Returned strategy instanceof PremiumBillingStrategy
    - strategy.supportedPlanType() returns PlanType.PREMIUM

#### should_throw_illegal_argument_exception_when_get_strategy_given_null_plan_type
- Description: Null plan type throws IllegalArgumentException
- Input: null
- Expected Output: IllegalArgumentException thrown
- Verification Points:
    - IllegalArgumentException is thrown

#### should_build_strategy_map_correctly_when_construct_given_list_of_strategies
- Description: Factory correctly maps all provided strategies by their supported plan type
- Input: List containing StandardBillingStrategy and PremiumBillingStrategy
- Expected Output: Factory that can resolve both plan types
- Verification Points:
    - getStrategy(STANDARD) returns StandardBillingStrategy
    - getStrategy(PREMIUM) returns PremiumBillingStrategy

---

## 6. JpaModelPricingRepositoryAdapter Test Scenarios (All New)

### Create `JpaModelPricingRepositoryAdapterTest` class
1. Create `JpaModelPricingRepositoryAdapterTest` class in `infrastructure/persistence/` package
2. Use @Mock annotation to mock `SpringDataModelPricingRepository` and `ModelPricingMapper`
3. Use @InjectMocks annotation to inject the `JpaModelPricingRepositoryAdapter` instance
4. Create test scenarios based on the prompts below
5. Generate test code for each test scenario

#### should_return_model_pricing_when_find_by_plan_id_and_model_id_given_existing_combination
- Description: Existing plan+model combination returns ModelPricing
- Input: planId="PLAN-STARTER", modelId="fast-model"
- Expected Output: Optional containing ModelPricing
- Verification Points:
    - SpringDataModelPricingRepository.findByPlanIdAndModelId() called with correct params
    - ModelPricingMapper.toDomain() called with returned PO
    - Returned Optional is present
    - Returned ModelPricing has correct planId and modelId

#### should_return_empty_optional_when_find_by_plan_id_and_model_id_given_non_existent_combination
- Description: Non-existent plan+model combination returns empty Optional
- Input: planId="PLAN-STARTER", modelId="unknown-model"
- Expected Output: Optional.empty()
- Verification Points:
    - SpringDataModelPricingRepository.findByPlanIdAndModelId() returns empty
    - Returned Optional is empty
    - ModelPricingMapper.toDomain() NOT called

---

## 7. Mapper Test Scenarios (All New)

### Create `ModelPricingMapperTest` class
1. Create `ModelPricingMapperTest` class in `infrastructure/persistence/mapper/` package
2. Instantiate `ModelPricingMapper` directly
3. Generate test code for each test scenario

#### should_map_all_fields_when_to_domain_given_valid_po
- Description: toDomain() maps all fields from PO to domain entity
- Input: ModelPricingPO with id, planId, modelId, overageRatePer1k, promptRatePer1k, completionRatePer1k, createdAt
- Expected Output: ModelPricing domain entity with all fields mapped
- Verification Points:
    - ModelPricing.id equals PO.id
    - ModelPricing.planId equals PO.planId
    - ModelPricing.modelId equals PO.modelId
    - ModelPricing.overageRatePer1k equals PO.overageRatePer1k
    - ModelPricing.promptRatePer1k equals PO.promptRatePer1k
    - ModelPricing.completionRatePer1k equals PO.completionRatePer1k
    - ModelPricing.createdAt equals PO.createdAt

### Create `PricingPlanMapperTest` class
1. Create `PricingPlanMapperTest` class in `infrastructure/persistence/mapper/` package
2. Instantiate `PricingPlanMapper` directly
3. Generate test code for each test scenario

#### should_convert_plan_type_string_to_enum_when_to_domain_given_standard_plan_type
- Description: toDomain() converts "STANDARD" string to PlanType.STANDARD enum
- Input: PricingPlanPO with planType="STANDARD"
- Expected Output: PricingPlan with planType=PlanType.STANDARD
- Verification Points:
    - PricingPlan.planType equals PlanType.STANDARD

#### should_convert_plan_type_string_to_enum_when_to_domain_given_premium_plan_type
- Description: toDomain() converts "PREMIUM" string to PlanType.PREMIUM enum
- Input: PricingPlanPO with planType="PREMIUM"
- Expected Output: PricingPlan with planType=PlanType.PREMIUM
- Verification Points:
    - PricingPlan.planType equals PlanType.PREMIUM

---

## 8. Integration Test Scenarios (New Only)

### Update `UsageControllerIntegrationTest` class
Add the following new test scenario to the existing `UsageControllerIntegrationTest` class.

#### should_return_201_with_premium_billing_when_submit_usage_given_premium_customer
- Description: End-to-end test for Premium plan billing with charge breakdown
- Input: POST `/api/usage` with `{"customerId": "CUST-PREMIUM", "modelId": "reasoning-model", "promptTokens": 10000, "completionTokens": 20000}`
- Expected Output: HTTP 201, bill with split charges persisted
- Verification Points:
    - HTTP status is 201 Created
    - Response contains `promptCharge` (not null, equals $0.30)
    - Response contains `completionCharge` (not null, equals $1.20)
    - Response `totalCharge` equals $1.50
    - Response `includedTokensUsed` equals 0
    - Response `overageTokens` equals 0
    - Response `modelId` equals "reasoning-model"

---

## 9. Constraints

- Test name should follow the format: `should_[expected result]_when_[action]_given_[condition]`
- All monetary assertions should use `isEqualByComparingTo()` for BigDecimal comparisons
- Use `@DisplayName` annotation for human-readable test descriptions
- Mock setup should use `when().thenReturn()` pattern consistently
- Integration tests should clean up test data between tests using `@Transactional` or `@DirtiesContext`
- Test data should match seeded migration data:
    - CUST-001, CUST-002, CUST-003: Standard plan customers
    - CUST-PREMIUM: Premium plan customer (with PLAN-PREMIUM subscription)
    - fast-model, reasoning-model: Configured model IDs
    - PLAN-PREMIUM rates: fast-model ($0.01/$0.02), reasoning-model ($0.03/$0.06)

---

## Summary of New Tests

| Test Class | New Tests | Status |
|------------|-----------|--------|
| UsageControllerTest | 3 | Add to existing |
| BillingServiceImplTest | 3 | Add to existing |
| StandardBillingStrategyTest | 5 | Create new class |
| PremiumBillingStrategyTest | 6 | Create new class |
| BillingStrategyFactoryTest | 4 | Create new class |
| JpaModelPricingRepositoryAdapterTest | 2 | Create new class |
| ModelPricingMapperTest | 1 | Create new class |
| PricingPlanMapperTest | 2 | Create new class |
| UsageControllerIntegrationTest | 1 | Add to existing |
| **Total** | **27** | |
