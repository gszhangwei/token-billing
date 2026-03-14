# Test Scenarios for Token Usage Billing API

## 1. UsageController Test Scenarios

### Create `UsageControllerTest` class
1. Create `UsageControllerTest` class in `src/test/java/org/tw/token_billing/controller/`
2. Use `@WebMvcTest(UsageController.class)` annotation to test the `UsageController` class
3. Use `@MockitoBean` annotation to mock the `BillingService` interface (Spring Boot 3.4+, replaces deprecated `@MockBean`)
4. Use `@Autowired` annotation to inject the `MockMvc` instance
5. Create test scenarios for `UsageController` based on the prompts below
6. Generate test code for each test scenario

#### should_return_201_created_with_bill_response_when_submit_usage_given_valid_request
- Description: Verify successful billing response when valid usage request is submitted
- Input: Valid `UsageRequest` with customerId="CUST-001", promptTokens=1000, completionTokens=500
- Expected Output: HTTP 201 Created with `BillResponse` containing all required fields
- Verification Points:
    - Response status is 201 Created
    - Response body contains `billId` (UUID format)
    - Response body contains `customerId` matching request
    - Response body contains `totalTokens` = 1500
    - Response body contains `includedTokensUsed`, `overageTokens`, `totalCharge`, `calculatedAt`
    - `BillingService.calculateBill()` is called exactly once with the request

#### should_return_400_bad_request_when_submit_usage_given_missing_customer_id
- Description: Verify validation error when customerId is missing
- Input: `UsageRequest` with customerId=null, promptTokens=1000, completionTokens=500
- Expected Output: HTTP 400 Bad Request with error message
- Verification Points:
    - Response status is 400 Bad Request
    - Response body contains error message "Customer ID is required"
    - `BillingService.calculateBill()` is never called

#### should_return_400_bad_request_when_submit_usage_given_negative_prompt_tokens
- Description: Verify validation error when promptTokens is negative
- Input: `UsageRequest` with customerId="CUST-001", promptTokens=-100, completionTokens=500
- Expected Output: HTTP 400 Bad Request with error message
- Verification Points:
    - Response status is 400 Bad Request
    - Response body contains error message "Token count cannot be negative"
    - `BillingService.calculateBill()` is never called

#### should_return_400_bad_request_when_submit_usage_given_negative_completion_tokens
- Description: Verify validation error when completionTokens is negative
- Input: `UsageRequest` with customerId="CUST-001", promptTokens=1000, completionTokens=-500
- Expected Output: HTTP 400 Bad Request with error message
- Verification Points:
    - Response status is 400 Bad Request
    - Response body contains error message "Token count cannot be negative"
    - `BillingService.calculateBill()` is never called

#### should_return_400_bad_request_when_submit_usage_given_null_prompt_tokens
- Description: Verify validation error when promptTokens is null
- Input: `UsageRequest` with customerId="CUST-001", promptTokens=null, completionTokens=500
- Expected Output: HTTP 400 Bad Request with error message
- Verification Points:
    - Response status is 400 Bad Request
    - Response body contains validation error message
    - `BillingService.calculateBill()` is never called

#### should_return_404_not_found_when_submit_usage_given_non_existent_customer
- Description: Verify 404 response when customer does not exist
- Input: Valid `UsageRequest` with customerId="INVALID-CUSTOMER"
- Expected Output: HTTP 404 Not Found with error message
- Verification Points:
    - Response status is 404 Not Found
    - Response body contains error message "Customer not found"
    - `BillingService.calculateBill()` throws `CustomerNotFoundException`

#### should_return_422_unprocessable_entity_when_submit_usage_given_no_active_subscription
- Description: Verify 422 response when customer has no active subscription
- Input: Valid `UsageRequest` with customerId="CUST-NO-SUB"
- Expected Output: HTTP 422 Unprocessable Entity with error message
- Verification Points:
    - Response status is 422 Unprocessable Entity
    - Response body contains error message "No active subscription found"
    - `BillingService.calculateBill()` throws `NoActiveSubscriptionException`

## 2. BillingService Test Scenarios

### Create `BillingServiceImplTest` class
1. Create `BillingServiceImplTest` class in `src/test/java/org/tw/token_billing/service/impl/`
2. Use `@ExtendWith(MockitoExtension.class)` annotation
3. Use `@Mock` annotation to mock `CustomerRepository`, `CustomerSubscriptionRepository`, `BillRepository` interfaces
4. Use `@InjectMocks` annotation to inject the `BillingServiceImpl` instance
5. Create test scenarios for `BillingServiceImpl` based on the prompts below
6. Generate test code for each test scenario

#### should_return_bill_with_zero_charge_when_calculate_bill_given_usage_within_quota
- Description: Verify bill calculation when usage is within monthly quota
- Input: UsageRequest(customerId="CUST-001", promptTokens=1000, completionTokens=500), remaining quota=10000
- Expected Output: Bill with includedTokensUsed=1500, overageTokens=0, totalCharge=0.00
- Verification Points:
    - `CustomerRepository.findById()` is called with customerId
    - `CustomerSubscriptionRepository.findActiveSubscription()` is called
    - `BillRepository.sumIncludedTokensUsedForMonth()` is called with correct month boundaries
    - `BillRepository.save()` is called with calculated Bill
    - Returned Bill has correct values: totalTokens=1500, includedTokensUsed=1500, overageTokens=0

#### should_return_bill_with_overage_charge_when_calculate_bill_given_usage_exceeds_quota
- Description: Verify bill calculation when usage exceeds remaining quota
- Input: UsageRequest(customerId="CUST-001", promptTokens=8000, completionTokens=5000), remaining quota=10000
- Expected Output: Bill with includedTokensUsed=10000, overageTokens=3000, totalCharge=(3000/1000)*overageRate
- Verification Points:
    - Bill totalTokens = 13000
    - Bill includedTokensUsed = 10000 (remaining quota)
    - Bill overageTokens = 3000
    - Bill totalCharge calculated correctly with BigDecimal precision

#### should_return_bill_with_full_overage_when_calculate_bill_given_zero_remaining_quota
- Description: Verify bill calculation when quota is already exhausted
- Input: UsageRequest(customerId="CUST-001", promptTokens=1000, completionTokens=500), remaining quota=0
- Expected Output: Bill with includedTokensUsed=0, overageTokens=1500, totalCharge=(1500/1000)*overageRate
- Verification Points:
    - Bill includedTokensUsed = 0
    - Bill overageTokens = 1500
    - totalCharge is calculated based on full usage

#### should_return_bill_with_zero_tokens_when_calculate_bill_given_zero_usage
- Description: Verify bill calculation for zero token submission
- Input: UsageRequest(customerId="CUST-001", promptTokens=0, completionTokens=0)
- Expected Output: Bill with totalTokens=0, includedTokensUsed=0, overageTokens=0, totalCharge=0.00
- Verification Points:
    - Bill is created successfully with all zeros
    - totalCharge is exactly 0.00

#### should_throw_customer_not_found_exception_when_calculate_bill_given_invalid_customer_id
- Description: Verify exception when customer does not exist
- Input: UsageRequest with non-existent customerId
- Expected Output: CustomerNotFoundException is thrown
- Verification Points:
    - `CustomerRepository.findById()` returns empty Optional
    - `CustomerNotFoundException` is thrown with correct customerId
    - `CustomerSubscriptionRepository` is never called

#### should_throw_no_active_subscription_exception_when_calculate_bill_given_customer_without_subscription
- Description: Verify exception when customer has no active subscription
- Input: UsageRequest for customer without active subscription
- Expected Output: NoActiveSubscriptionException is thrown
- Verification Points:
    - `CustomerRepository.findById()` returns customer
    - `CustomerSubscriptionRepository.findActiveSubscription()` returns empty Optional
    - `NoActiveSubscriptionException` is thrown with correct customerId
    - `BillRepository` is never called

#### should_use_correct_month_boundaries_when_calculate_bill_given_mid_month_request
- Description: Verify correct month boundary calculation for quota aggregation
- Input: UsageRequest submitted on 2026-03-15
- Expected Output: Month boundaries are 2026-03-01 00:00:00 to 2026-04-01 00:00:00
- Verification Points:
    - `BillRepository.sumIncludedTokensUsedForMonth()` is called with correct monthStart and monthEnd
    - Month start is first day of current month at 00:00:00
    - Month end is first day of next month at 00:00:00

## 3. Bill Domain Entity Test Scenarios

### Create `BillTest` class
1. Create `BillTest` class in `src/test/java/org/tw/token_billing/domain/`
2. Create test scenarios for `Bill.create()` factory method based on the prompts below
3. Generate test code for each test scenario

#### should_create_bill_with_correct_totals_when_create_given_valid_inputs
- Description: Verify Bill creation with correct token totals
- Input: customerId="CUST-001", promptTokens=1000, completionTokens=500, remainingQuota=10000, overageRate=0.02
- Expected Output: Bill with totalTokens=1500
- Verification Points:
    - Bill id is generated (UUID)
    - Bill customerId matches input
    - Bill totalTokens = promptTokens + completionTokens
    - Bill calculatedAt is set (not null, recent timestamp)

#### should_create_bill_with_all_included_when_create_given_usage_within_quota
- Description: Verify quota deduction when usage is within quota
- Input: promptTokens=1000, completionTokens=500, remainingQuota=10000
- Expected Output: includedTokensUsed=1500, overageTokens=0, totalCharge=0.00
- Verification Points:
    - includedTokensUsed equals totalTokens
    - overageTokens is zero
    - totalCharge is exactly 0.00 (BigDecimal comparison)

#### should_create_bill_with_partial_included_when_create_given_usage_exceeds_quota
- Description: Verify partial quota usage when usage exceeds remaining quota
- Input: promptTokens=8000, completionTokens=5000, remainingQuota=10000, overageRate=0.02
- Expected Output: includedTokensUsed=10000, overageTokens=3000
- Verification Points:
    - includedTokensUsed equals remainingQuota (10000)
    - overageTokens = totalTokens - includedTokensUsed (3000)
    - totalCharge = (3000 / 1000) * 0.02 = 0.06

#### should_create_bill_with_all_overage_when_create_given_zero_remaining_quota
- Description: Verify all tokens go to overage when quota is exhausted
- Input: promptTokens=1000, completionTokens=500, remainingQuota=0, overageRate=0.02
- Expected Output: includedTokensUsed=0, overageTokens=1500
- Verification Points:
    - includedTokensUsed is zero
    - overageTokens equals totalTokens
    - totalCharge = (1500 / 1000) * 0.02 = 0.03

#### should_create_bill_with_all_overage_when_create_given_negative_remaining_quota
- Description: Verify handling of negative remaining quota (already over quota)
- Input: promptTokens=1000, completionTokens=500, remainingQuota=-500, overageRate=0.02
- Expected Output: includedTokensUsed=0, overageTokens=1500
- Verification Points:
    - includedTokensUsed is zero (not negative)
    - overageTokens equals totalTokens
    - Negative quota treated as zero remaining

#### should_create_bill_with_correct_charge_precision_when_create_given_fractional_calculation
- Description: Verify BigDecimal precision in charge calculation
- Input: promptTokens=1234, completionTokens=567, remainingQuota=0, overageRate=0.0234
- Expected Output: totalCharge calculated with correct precision, rounded to 2 decimals HALF_UP
- Verification Points:
    - totalCharge has scale of 2
    - Calculation uses HALF_UP rounding
    - No floating-point precision errors

## 4. BillResponse DTO Test Scenarios

### Create `BillResponseTest` class
1. Create `BillResponseTest` class in `src/test/java/org/tw/token_billing/dto/`
2. Create test scenarios for `BillResponse.fromBill()` factory method
3. Generate test code for each test scenario

#### should_map_all_fields_when_from_bill_given_complete_bill
- Description: Verify all fields are correctly mapped from Bill to BillResponse
- Input: Bill with all fields populated
- Expected Output: BillResponse with matching field values
- Verification Points:
    - billId equals Bill.id
    - customerId equals Bill.customerId
    - totalTokens equals Bill.totalTokens
    - includedTokensUsed equals Bill.includedTokensUsed
    - overageTokens equals Bill.overageTokens
    - totalCharge equals Bill.totalCharge
    - calculatedAt equals Bill.calculatedAt

## 5. Repository Adapter Test Scenarios

### Create `JpaBillRepositoryAdapterTest` class
1. Create `JpaBillRepositoryAdapterTest` class in `src/test/java/org/tw/token_billing/infrastructure/persistence/`
2. Use `@ExtendWith(MockitoExtension.class)` annotation
3. Use `@Mock` annotation to mock `SpringDataBillRepository` and `BillMapper`
4. Use `@InjectMocks` annotation to inject the `JpaBillRepositoryAdapter` instance
5. Create test scenarios based on the prompts below

#### should_save_and_return_domain_bill_when_save_given_valid_bill
- Description: Verify save operation correctly maps between domain and persistence
- Input: Valid Bill domain entity
- Expected Output: Persisted Bill domain entity
- Verification Points:
    - `BillMapper.toPO()` is called with domain Bill
    - `SpringDataBillRepository.save()` is called with BillPO
    - `BillMapper.toDomain()` is called with saved BillPO
    - Returned Bill matches expected values

#### should_return_sum_of_included_tokens_when_sum_for_month_given_existing_bills
- Description: Verify month aggregation delegates correctly
- Input: customerId, monthStart, monthEnd
- Expected Output: Sum of includedTokensUsed from repository
- Verification Points:
    - `SpringDataBillRepository.sumIncludedTokensUsedForMonth()` is called with correct parameters
    - Return value matches repository result

## 6. Integration Test Scenarios

### Create `UsageControllerIntegrationTest` class
1. Create `UsageControllerIntegrationTest` class in `src/test/java/org/tw/token_billing/`
2. Use `@SpringBootTest` annotation
3. Use `@AutoConfigureMockMvc` annotation
4. Use `@Transactional` annotation for test isolation
5. Create test scenarios for end-to-end API testing

#### should_return_201_and_persist_bill_when_submit_usage_given_valid_customer_with_subscription
- Description: End-to-end test for successful billing flow
- Input: POST /api/usage with valid request for seeded customer CUST-001
- Expected Output: HTTP 201 with BillResponse, bill persisted in database
- Verification Points:
    - Response status is 201 Created
    - Response body contains valid BillResponse
    - Bill is persisted in database (can be queried)
    - Quota tracking works correctly on subsequent requests

#### should_return_404_when_submit_usage_given_non_existent_customer
- Description: End-to-end test for customer not found scenario
- Input: POST /api/usage with non-existent customerId
- Expected Output: HTTP 404 with ErrorResponse
- Verification Points:
    - Response status is 404 Not Found
    - Response body contains "Customer not found" message
    - No bill is persisted

#### should_return_422_when_submit_usage_given_customer_without_active_subscription
- Description: End-to-end test for no active subscription scenario
- Input: POST /api/usage for customer without active subscription (need test data setup)
- Expected Output: HTTP 422 with ErrorResponse
- Verification Points:
    - Response status is 422 Unprocessable Entity
    - Response body contains "No active subscription found" message
    - No bill is persisted

#### should_track_quota_correctly_when_submit_multiple_usages_given_same_customer
- Description: End-to-end test for quota tracking across multiple requests
- Input: Multiple POST /api/usage requests for same customer within same month
- Expected Output: Quota decreases with each request, overage kicks in when exhausted
- Verification Points:
    - First request uses quota
    - Subsequent requests see reduced remaining quota
    - When quota exhausted, overage charges apply
    - All bills are persisted correctly

#### should_return_400_when_submit_usage_given_invalid_json
- Description: End-to-end test for malformed request handling
- Input: POST /api/usage with invalid JSON body
- Expected Output: HTTP 400 Bad Request
- Verification Points:
    - Response status is 400 Bad Request
    - No bill is persisted

## 7. Constraints

- Test name should follow the format: `should_[expected result]_when_[action]_given_[condition]`
- Use AssertJ for assertions (`assertThat()`)
- Use Mockito for mocking (`when()`, `verify()`)
- Use `@DisplayName` annotation for readable test descriptions
- Each test should be independent and not rely on other tests
- Use `@BeforeEach` for common test setup
- Use test data builders or factory methods for creating test objects
- Verify both happy path and edge cases
- For BigDecimal comparisons, use `compareTo()` or AssertJ's `isEqualByComparingTo()`
- Integration tests should use `@Transactional` for automatic rollback
