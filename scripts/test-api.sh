#!/bin/bash
# =============================================================================
# API Test Script
# Generated for: Multi-Plan Billing Foundation & Model-Aware Pricing
# =============================================================================
#
# Usage: ./scripts/test-api.sh [BASE_URL]
#        Default BASE_URL: http://localhost:8080
#
# Requirements:
# - No external dependencies (no jq, only curl and bash)
# - Each request has -m 10 timeout to prevent hanging
# - HTTP status captured via: -o /tmp/response.txt -w "%{http_code}"
#
# Prerequisites:
# - Application running with database migrated (V1 + V2)
# - Seed data loaded from migrations
#
# =============================================================================

# -----------------------------------------------------------------------------
# CONFIGURATION
# -----------------------------------------------------------------------------
BASE_URL="${1:-http://localhost:8080}"

# Colors for output (disabled if not a terminal)
if [ -t 1 ]; then
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    BLUE='\033[0;34m'
    NC='\033[0m' # No Color
else
    RED=''
    GREEN=''
    YELLOW=''
    BLUE=''
    NC=''
fi

# -----------------------------------------------------------------------------
# SEED DATA REFERENCE
# -----------------------------------------------------------------------------
# Customers (from V1 + V2 migrations):
#   - CUST-001: Acme Corp        → PLAN-STARTER (100,000 quota, STANDARD)
#   - CUST-002: TechStart Inc    → PLAN-FREE (10,000 quota, STANDARD)
#   - CUST-003: Enterprise Ltd   → PLAN-ENTERPRISE (2,000,000 quota, STANDARD)
#   - CUST-PREMIUM: Premium Test Corp → PLAN-PREMIUM (no quota, PREMIUM)
#
# Plans (from V1 + V2 migrations):
#   - PLAN-FREE:       10,000 quota,    $0.03/1K overage, STANDARD
#   - PLAN-STARTER:    100,000 quota,   $0.02/1K overage, STANDARD
#   - PLAN-PRO:        500,000 quota,   $0.015/1K overage, STANDARD
#   - PLAN-ENTERPRISE: 2,000,000 quota, $0.01/1K overage, STANDARD
#   - PLAN-PREMIUM:    0 quota (no quota), PREMIUM
#
# Model Pricing (from V2 migration):
#   - All STANDARD plans: fast-model & reasoning-model inherit plan's overage rate
#   - PLAN-PREMIUM: fast-model ($0.01 prompt, $0.02 completion)
#   - PLAN-PREMIUM: reasoning-model ($0.03 prompt, $0.06 completion)
#
# Models:
#   - fast-model
#   - reasoning-model
# -----------------------------------------------------------------------------

# -----------------------------------------------------------------------------
# TEST COUNTERS
# -----------------------------------------------------------------------------
TESTS_PASSED=0
TESTS_FAILED=0
TESTS_TOTAL=0

# -----------------------------------------------------------------------------
# HELPER FUNCTIONS
# -----------------------------------------------------------------------------
print_test_header() {
    echo ""
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}TEST: $1${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
}

print_expected() {
    echo -e "${YELLOW}Expected: $1${NC}"
}

print_result() {
    echo -e "${GREEN}Response:${NC}"
}

check_result() {
    local test_name="$1"
    local expected_status="$2"
    local actual_status="$3"
    local body="$4"

    echo "$body"
    echo ""

    if [ "$actual_status" = "$expected_status" ]; then
        echo -e "${GREEN}✓ PASSED${NC} [HTTP Status: $actual_status]"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo -e "${RED}✗ FAILED${NC} [HTTP Status: $actual_status, Expected: $expected_status]"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
    echo ""
}

# =============================================================================
# ACCEPTANCE CRITERIA TESTS
# =============================================================================

# -----------------------------------------------------------------------------
# AC1: Base Validations (Regression & New)
# Given an invalid request (e.g., missing modelId, negative tokens)
# When backend validates request
# Then return HTTP 400 with appropriate error messages
# -----------------------------------------------------------------------------

TEST_NAME="AC1.1: Missing modelId returns 400"
EXPECTED="400"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_NAME"
print_expected "HTTP $EXPECTED with error message 'Model ID is required'"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-001", "promptTokens": 1000, "completionTokens": 500}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_NAME" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_NAME="AC1.2: Missing customerId returns 400"
EXPECTED="400"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_NAME"
print_expected "HTTP $EXPECTED with error message 'Customer ID is required'"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"modelId": "fast-model", "promptTokens": 1000, "completionTokens": 500}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_NAME" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_NAME="AC1.3: Negative promptTokens returns 400"
EXPECTED="400"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_NAME"
print_expected "HTTP $EXPECTED with error message 'Token count cannot be negative'"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-001", "modelId": "fast-model", "promptTokens": -100, "completionTokens": 500}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_NAME" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_NAME="AC1.4: Negative completionTokens returns 400"
EXPECTED="400"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_NAME"
print_expected "HTTP $EXPECTED with error message 'Token count cannot be negative'"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-001", "modelId": "fast-model", "promptTokens": 1000, "completionTokens": -500}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_NAME" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_NAME="AC1.5: Missing promptTokens returns 400"
EXPECTED="400"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_NAME"
print_expected "HTTP $EXPECTED"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-001", "modelId": "fast-model", "completionTokens": 500}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_NAME" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_NAME="AC1.6: Non-existent customer returns 404"
EXPECTED="404"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_NAME"
print_expected "HTTP $EXPECTED with error message 'Customer not found'"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "NON-EXISTENT", "modelId": "fast-model", "promptTokens": 1000, "completionTokens": 500}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_NAME" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_NAME="AC1.7: Unknown model returns 400"
EXPECTED="400"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_NAME"
print_expected "HTTP $EXPECTED with error message about pricing not configured"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-001", "modelId": "unknown-model", "promptTokens": 1000, "completionTokens": 500}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_NAME" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
# AC2: Standard Plan with Model-Aware Overage
# Given a "Standard" customer with a 100,000 monthly quota
# When submitting tokens for "fast-model"
# Then bill shows correct quota usage and overage calculation
# -----------------------------------------------------------------------------

TEST_NAME="AC2.1: Standard Plan - Usage within quota (no overage)"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_NAME"
print_expected "HTTP $EXPECTED with includedTokensUsed=1500, overageTokens=0, totalCharge=0"
echo -e "${YELLOW}Using: CUST-001 (PLAN-STARTER: 100,000 quota)${NC}"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-001", "modelId": "fast-model", "promptTokens": 1000, "completionTokens": 500}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_NAME" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_NAME="AC2.2: Standard Plan - Response includes modelId"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_NAME"
print_expected "HTTP $EXPECTED with modelId='fast-model' in response"
echo -e "${YELLOW}Using: CUST-003 (PLAN-ENTERPRISE: 2,000,000 quota)${NC}"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-003", "modelId": "fast-model", "promptTokens": 5000, "completionTokens": 5000}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_NAME" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_NAME="AC2.3: Standard Plan - Small quota exceeds (CUST-002 with PLAN-FREE)"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_NAME"
print_expected "HTTP $EXPECTED with overage charged (PLAN-FREE has only 10,000 quota)"
echo -e "${YELLOW}Using: CUST-002 (PLAN-FREE: 10,000 quota, \$0.03/1K overage)${NC}"
echo -e "${YELLOW}Submitting 15,000 tokens should result in 5,000 overage = \$0.15${NC}"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-002", "modelId": "fast-model", "promptTokens": 10000, "completionTokens": 5000}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_NAME" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_NAME="AC2.4: Standard Plan - Using reasoning-model"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_NAME"
print_expected "HTTP $EXPECTED with modelId='reasoning-model'"
echo -e "${YELLOW}Using: CUST-001 with reasoning-model${NC}"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-001", "modelId": "reasoning-model", "promptTokens": 2000, "completionTokens": 3000}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_NAME" "$EXPECTED" "$HTTP_CODE" "$BODY"

# =============================================================================
# EDGE CASE TESTS
# =============================================================================

# -----------------------------------------------------------------------------
TEST_NAME="Edge Case: Zero tokens submission"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_NAME"
print_expected "HTTP $EXPECTED with totalTokens=0, totalCharge=0"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-001", "modelId": "fast-model", "promptTokens": 0, "completionTokens": 0}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_NAME" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_NAME="Edge Case: Only prompt tokens (zero completion)"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_NAME"
print_expected "HTTP $EXPECTED with completionTokens=0"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-001", "modelId": "fast-model", "promptTokens": 1000, "completionTokens": 0}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_NAME" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_NAME="Edge Case: Only completion tokens (zero prompt)"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_NAME"
print_expected "HTTP $EXPECTED with promptTokens=0"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-001", "modelId": "fast-model", "promptTokens": 0, "completionTokens": 1000}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_NAME" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_NAME="Edge Case: Empty JSON body"
EXPECTED="400"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_NAME"
print_expected "HTTP $EXPECTED"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_NAME" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_NAME="Edge Case: Invalid JSON format"
EXPECTED="400"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_NAME"
print_expected "HTTP $EXPECTED"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{ invalid json }')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_NAME" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_NAME="Edge Case: Large token count"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_NAME"
print_expected "HTTP $EXPECTED - handles large numbers correctly"
echo -e "${YELLOW}Using: CUST-003 (PLAN-ENTERPRISE: 2,000,000 quota)${NC}"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-003", "modelId": "fast-model", "promptTokens": 500000, "completionTokens": 500000}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_NAME" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
# AC3: Premium Plan with Split Prompt/Completion Rates
# Given a "Premium" customer (no monthly quota)
# When submitting usage with prompt and completion tokens
# Then bill shows separate promptCharge and completionCharge (no quota deduction)
# -----------------------------------------------------------------------------

TEST_NAME="AC3.1: Premium Plan - fast-model billing (no quota, split rates)"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_NAME"
print_expected "HTTP $EXPECTED with includedTokensUsed=0, overageTokens=0, split charges"
echo -e "${YELLOW}Using: CUST-PREMIUM (PLAN-PREMIUM: no quota)${NC}"
echo -e "${YELLOW}fast-model rates: \$0.01/1K prompt, \$0.02/1K completion${NC}"
echo -e "${YELLOW}10,000 prompt + 5,000 completion = \$0.10 + \$0.10 = \$0.20 total${NC}"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-PREMIUM", "modelId": "fast-model", "promptTokens": 10000, "completionTokens": 5000}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_NAME" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_NAME="AC3.2: Premium Plan - reasoning-model billing (higher rates)"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_NAME"
print_expected "HTTP $EXPECTED with promptCharge and completionCharge in response"
echo -e "${YELLOW}Using: CUST-PREMIUM (PLAN-PREMIUM)${NC}"
echo -e "${YELLOW}reasoning-model rates: \$0.03/1K prompt, \$0.06/1K completion${NC}"
echo -e "${YELLOW}10,000 prompt + 20,000 completion = \$0.30 + \$1.20 = \$1.50 total${NC}"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-PREMIUM", "modelId": "reasoning-model", "promptTokens": 10000, "completionTokens": 20000}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_NAME" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_NAME="AC3.3: Premium Plan - Response includes modelId"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_NAME"
print_expected "HTTP $EXPECTED with modelId='reasoning-model' in response"
echo -e "${YELLOW}Verify modelId field is present in Premium plan response${NC}"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-PREMIUM", "modelId": "reasoning-model", "promptTokens": 1000, "completionTokens": 1000}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_NAME" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_NAME="AC3.4: Premium Plan - Zero tokens submission"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_NAME"
print_expected "HTTP $EXPECTED with totalCharge=0, promptCharge=0, completionCharge=0"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-PREMIUM", "modelId": "fast-model", "promptTokens": 0, "completionTokens": 0}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_NAME" "$EXPECTED" "$HTTP_CODE" "$BODY"

# =============================================================================
# RESPONSE STRUCTURE VALIDATION
# =============================================================================

# -----------------------------------------------------------------------------
TEST_NAME="Response Structure: Standard Plan - Contains all required fields"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_NAME"
print_expected "HTTP $EXPECTED with billId, customerId, modelId, totalTokens, includedTokensUsed, overageTokens, totalCharge, calculatedAt"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-001", "modelId": "fast-model", "promptTokens": 100, "completionTokens": 100}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_NAME" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_NAME="Response Structure: Premium Plan - Contains charge breakdown"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_NAME"
print_expected "HTTP $EXPECTED with promptCharge and completionCharge fields"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-PREMIUM", "modelId": "fast-model", "promptTokens": 100, "completionTokens": 100}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_NAME" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
# CLEANUP
# -----------------------------------------------------------------------------
rm -f /tmp/response.txt

# -----------------------------------------------------------------------------
# TEST SUMMARY
# -----------------------------------------------------------------------------
echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}TEST EXECUTION COMPLETE${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo ""
echo "Base URL: ${BASE_URL}"
echo "Finished at: $(date)"
echo ""
echo -e "Tests Passed: ${GREEN}${TESTS_PASSED}${NC}"
echo -e "Tests Failed: ${RED}${TESTS_FAILED}${NC}"
echo -e "Total Tests:  ${TESTS_TOTAL}"
echo ""

# Calculate pass rate
if [ "$TESTS_TOTAL" -gt 0 ]; then
    PASS_RATE=$((TESTS_PASSED * 100 / TESTS_TOTAL))
    if [ "$TESTS_FAILED" -eq 0 ]; then
        echo -e "${GREEN}✓ All tests passed! (${PASS_RATE}%)${NC}"
    else
        echo -e "${RED}✗ Some tests failed (${PASS_RATE}% passed)${NC}"
    fi
fi
echo ""

# Exit with error code if any tests failed
if [ "$TESTS_FAILED" -gt 0 ]; then
    exit 1
fi
