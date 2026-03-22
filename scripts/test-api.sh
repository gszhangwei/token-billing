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
#
# TEST CASE OVERVIEW (Human-Reviewable)
# =============================================================================
#
# ┌───────────────────────────────────────────────────────────────────────────────────────┐
# │ VALIDATION ERROR TESTS (AC1)                                                          │
# ├─────────┬────────────────────────────┬──────────────┬─────────────────┬───────┬───────┤
# │ Test ID │ Description                │ Customer     │ Model           │ HTTP  │ Error │
# ├─────────┼────────────────────────────┼──────────────┼─────────────────┼───────┼───────┤
# │ AC1.1   │ Missing modelId            │ CUST-001     │ (missing)       │ 400   │ Model ID is required     │
# │ AC1.2   │ Missing customerId         │ (missing)    │ fast-model      │ 400   │ Customer ID is required  │
# │ AC1.3   │ Negative promptTokens      │ CUST-001     │ fast-model      │ 400   │ Token count cannot be negative │
# │ AC1.4   │ Negative completionTokens  │ CUST-001     │ fast-model      │ 400   │ Token count cannot be negative │
# │ AC1.5   │ Missing promptTokens       │ CUST-001     │ fast-model      │ 400   │ (validation error)       │
# │ AC1.6   │ Non-existent customer      │ NON-EXISTENT │ fast-model      │ 404   │ Customer not found       │
# │ AC1.7   │ Unknown model              │ CUST-001     │ unknown-model   │ 400   │ Pricing not configured   │
# └─────────┴────────────────────────────┴──────────────┴─────────────────┴───────┴───────┘
#
# ┌───────────────────────────────────────────────────────────────────────────────────────┐
# │ STANDARD PLAN TESTS (AC2) - Quota-based billing                                       │
# ├─────────┬────────────────────────┬──────────┬──────────┬────────┬──────┬───────┬──────┤
# │ Test ID │ Description            │ Customer │ Model    │ Prompt │ Comp │ HTTP  │Charge│
# ├─────────┼────────────────────────┼──────────┼──────────┼────────┼──────┼───────┼──────┤
# │ AC2.1   │ Within quota           │ CUST-001 │ fast     │ 1000   │ 500  │ 201   │ 0.00 │
# │ AC2.2   │ Response has modelId   │ CUST-003 │ fast     │ 5000   │ 5000 │ 201   │ 0.00 │
# │ AC2.3   │ Exceeds small quota    │ CUST-002 │ fast     │ 10000  │ 5000 │ 201   │ 0.15 │
# │ AC2.4   │ Using reasoning-model  │ CUST-001 │ reason   │ 2000   │ 3000 │ 201   │ 0.00 │
# └─────────┴────────────────────────┴──────────┴──────────┴────────┴──────┴───────┴──────┘
#
# ┌───────────────────────────────────────────────────────────────────────────────────────┐
# │ PREMIUM PLAN TESTS (AC3) - Split prompt/completion billing                            │
# ├─────────┬────────────────────────┬──────────────┬─────────┬────────┬──────┬───────────┤
# │ Test ID │ Description            │ Customer     │ Model   │ Prompt │ Comp │ Total     │
# ├─────────┼────────────────────────┼──────────────┼─────────┼────────┼──────┼───────────┤
# │ AC3.1   │ fast-model billing     │ CUST-PREMIUM │ fast    │ 10000  │ 5000 │ 0.20      │
# │ AC3.2   │ reasoning-model        │ CUST-PREMIUM │ reason  │ 10000  │ 20000│ 1.50      │
# │ AC3.3   │ Response has modelId   │ CUST-PREMIUM │ reason  │ 1000   │ 1000 │ 0.09      │
# │ AC3.4   │ Zero tokens            │ CUST-PREMIUM │ fast    │ 0      │ 0    │ 0.00      │
# └─────────┴────────────────────────┴──────────────┴─────────┴────────┴──────┴───────────┘
#
# ┌───────────────────────────────────────────────────────────────────────────────────────┐
# │ EDGE CASE TESTS                                                                       │
# ├─────────┬────────────────────────┬──────────┬──────────┬────────┬──────┬──────────────┤
# │ Test ID │ Description            │ Customer │ Model    │ Prompt │ Comp │ HTTP         │
# ├─────────┼────────────────────────┼──────────┼──────────┼────────┼──────┼──────────────┤
# │ EDGE1   │ Zero tokens            │ CUST-001 │ fast     │ 0      │ 0    │ 201          │
# │ EDGE2   │ Only prompt tokens     │ CUST-001 │ fast     │ 1000   │ 0    │ 201          │
# │ EDGE3   │ Only completion tokens │ CUST-001 │ fast     │ 0      │ 1000 │ 201          │
# │ EDGE4   │ Empty JSON body        │ -        │ -        │ -      │ -    │ 400          │
# │ EDGE5   │ Invalid JSON format    │ -        │ -        │ -      │ -    │ 400          │
# │ EDGE6   │ Large token count      │ CUST-003 │ fast     │ 500000 │500000│ 201          │
# └─────────┴────────────────────────┴──────────┴──────────┴────────┴──────┴──────────────┘
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
    CYAN='\033[0;36m'
    NC='\033[0m' # No Color
else
    RED=''
    GREEN=''
    YELLOW=''
    BLUE=''
    CYAN=''
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
# TEST COUNTERS AND RESULT TRACKING
# -----------------------------------------------------------------------------
TESTS_PASSED=0
TESTS_FAILED=0
TESTS_TOTAL=0

# Arrays to track results for final summary table
declare -a TEST_IDS
declare -a TEST_DESCRIPTIONS
declare -a EXPECTED_STATUS
declare -a ACTUAL_STATUS
declare -a TEST_RESULTS

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

# Record test result for final summary table
record_result() {
    TEST_IDS+=("$1")
    TEST_DESCRIPTIONS+=("$2")
    EXPECTED_STATUS+=("$3")
    ACTUAL_STATUS+=("$4")
    TEST_RESULTS+=("$5")
}

check_result() {
    local test_id="$1"
    local test_desc="$2"
    local expected_status="$3"
    local actual_status="$4"
    local body="$5"

    echo "$body"
    echo ""

    if [ "$actual_status" = "$expected_status" ]; then
        echo -e "${GREEN}✓ PASSED${NC} [HTTP Status: $actual_status]"
        TESTS_PASSED=$((TESTS_PASSED + 1))
        record_result "$test_id" "$test_desc" "$expected_status" "$actual_status" "PASS"
    else
        echo -e "${RED}✗ FAILED${NC} [HTTP Status: $actual_status, Expected: $expected_status]"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        record_result "$test_id" "$test_desc" "$expected_status" "$actual_status" "FAIL"
    fi
    echo ""
}

# Print final results table
print_results_table() {
    echo ""
    echo -e "${CYAN}┌─────────────────────────────────────────────────────────────────────────────┐${NC}"
    echo -e "${CYAN}│                         TEST RESULTS SUMMARY                                │${NC}"
    echo -e "${CYAN}├──────────┬────────────────────────────────┬──────────┬──────────┬──────────┤${NC}"
    echo -e "${CYAN}│ Test ID  │ Description                    │ Expected │ Actual   │ Result   │${NC}"
    echo -e "${CYAN}├──────────┼────────────────────────────────┼──────────┼──────────┼──────────┤${NC}"
    
    for i in "${!TEST_IDS[@]}"; do
        local result_color="${GREEN}"
        if [ "${TEST_RESULTS[$i]}" = "FAIL" ]; then
            result_color="${RED}"
        fi
        printf "${CYAN}│${NC} %-8s ${CYAN}│${NC} %-30s ${CYAN}│${NC} %-8s ${CYAN}│${NC} %-8s ${CYAN}│${NC} ${result_color}%-8s${NC} ${CYAN}│${NC}\n" \
            "${TEST_IDS[$i]}" \
            "${TEST_DESCRIPTIONS[$i]:0:30}" \
            "${EXPECTED_STATUS[$i]}" \
            "${ACTUAL_STATUS[$i]}" \
            "${TEST_RESULTS[$i]}"
    done
    
    echo -e "${CYAN}└──────────┴────────────────────────────────┴──────────┴──────────┴──────────┘${NC}"
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

TEST_ID="AC1.1"
TEST_DESC="Missing modelId returns 400"
EXPECTED="400"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_ID: $TEST_DESC"
print_expected "HTTP $EXPECTED with error message 'Model ID is required'"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-001", "promptTokens": 1000, "completionTokens": 500}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_ID="AC1.2"
TEST_DESC="Missing customerId returns 400"
EXPECTED="400"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_ID: $TEST_DESC"
print_expected "HTTP $EXPECTED with error message 'Customer ID is required'"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"modelId": "fast-model", "promptTokens": 1000, "completionTokens": 500}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_ID="AC1.3"
TEST_DESC="Negative promptTokens returns 400"
EXPECTED="400"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_ID: $TEST_DESC"
print_expected "HTTP $EXPECTED with error message 'Token count cannot be negative'"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-001", "modelId": "fast-model", "promptTokens": -100, "completionTokens": 500}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_ID="AC1.4"
TEST_DESC="Negative completionTokens returns 400"
EXPECTED="400"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_ID: $TEST_DESC"
print_expected "HTTP $EXPECTED with error message 'Token count cannot be negative'"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-001", "modelId": "fast-model", "promptTokens": 1000, "completionTokens": -500}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_ID="AC1.5"
TEST_DESC="Missing promptTokens returns 400"
EXPECTED="400"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_ID: $TEST_DESC"
print_expected "HTTP $EXPECTED"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-001", "modelId": "fast-model", "completionTokens": 500}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_ID="AC1.6"
TEST_DESC="Non-existent customer returns 404"
EXPECTED="404"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_ID: $TEST_DESC"
print_expected "HTTP $EXPECTED with error message 'Customer not found'"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "NON-EXISTENT", "modelId": "fast-model", "promptTokens": 1000, "completionTokens": 500}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_ID="AC1.7"
TEST_DESC="Unknown model returns 400"
EXPECTED="400"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_ID: $TEST_DESC"
print_expected "HTTP $EXPECTED with error message about pricing not configured"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-001", "modelId": "unknown-model", "promptTokens": 1000, "completionTokens": 500}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
# AC2: Standard Plan with Model-Aware Overage
# Given a "Standard" customer with a 100,000 monthly quota
# When submitting tokens for "fast-model"
# Then bill shows correct quota usage and overage calculation
# -----------------------------------------------------------------------------

TEST_ID="AC2.1"
TEST_DESC="Standard - Within quota (no overage)"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_ID: $TEST_DESC"
print_expected "HTTP $EXPECTED with includedTokensUsed=1500, overageTokens=0, totalCharge=0"
echo -e "${YELLOW}Using: CUST-001 (PLAN-STARTER: 100,000 quota)${NC}"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-001", "modelId": "fast-model", "promptTokens": 1000, "completionTokens": 500}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_ID="AC2.2"
TEST_DESC="Standard - Response has modelId"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_ID: $TEST_DESC"
print_expected "HTTP $EXPECTED with modelId='fast-model' in response"
echo -e "${YELLOW}Using: CUST-003 (PLAN-ENTERPRISE: 2,000,000 quota)${NC}"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-003", "modelId": "fast-model", "promptTokens": 5000, "completionTokens": 5000}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_ID="AC2.3"
TEST_DESC="Standard - Exceeds small quota"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_ID: $TEST_DESC"
print_expected "HTTP $EXPECTED with overage charged (PLAN-FREE has only 10,000 quota)"
echo -e "${YELLOW}Using: CUST-002 (PLAN-FREE: 10,000 quota, \$0.03/1K overage)${NC}"
echo -e "${YELLOW}Submitting 15,000 tokens should result in 5,000 overage = \$0.15${NC}"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-002", "modelId": "fast-model", "promptTokens": 10000, "completionTokens": 5000}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_ID="AC2.4"
TEST_DESC="Standard - Using reasoning-model"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_ID: $TEST_DESC"
print_expected "HTTP $EXPECTED with modelId='reasoning-model'"
echo -e "${YELLOW}Using: CUST-001 with reasoning-model${NC}"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-001", "modelId": "reasoning-model", "promptTokens": 2000, "completionTokens": 3000}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"

# =============================================================================
# EDGE CASE TESTS
# =============================================================================

# -----------------------------------------------------------------------------
TEST_ID="EDGE1"
TEST_DESC="Zero tokens submission"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_ID: $TEST_DESC"
print_expected "HTTP $EXPECTED with totalTokens=0, totalCharge=0"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-001", "modelId": "fast-model", "promptTokens": 0, "completionTokens": 0}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_ID="EDGE2"
TEST_DESC="Only prompt tokens (zero completion)"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_ID: $TEST_DESC"
print_expected "HTTP $EXPECTED with completionTokens=0"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-001", "modelId": "fast-model", "promptTokens": 1000, "completionTokens": 0}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_ID="EDGE3"
TEST_DESC="Only completion tokens (zero prompt)"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_ID: $TEST_DESC"
print_expected "HTTP $EXPECTED with promptTokens=0"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-001", "modelId": "fast-model", "promptTokens": 0, "completionTokens": 1000}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_ID="EDGE4"
TEST_DESC="Empty JSON body"
EXPECTED="400"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_ID: $TEST_DESC"
print_expected "HTTP $EXPECTED"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_ID="EDGE5"
TEST_DESC="Invalid JSON format"
EXPECTED="400"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_ID: $TEST_DESC"
print_expected "HTTP $EXPECTED"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{ invalid json }')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_ID="EDGE6"
TEST_DESC="Large token count"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_ID: $TEST_DESC"
print_expected "HTTP $EXPECTED - handles large numbers correctly"
echo -e "${YELLOW}Using: CUST-003 (PLAN-ENTERPRISE: 2,000,000 quota)${NC}"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-003", "modelId": "fast-model", "promptTokens": 500000, "completionTokens": 500000}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
# AC3: Premium Plan with Split Prompt/Completion Rates
# Given a "Premium" customer (no monthly quota)
# When submitting usage with prompt and completion tokens
# Then bill shows separate promptCharge and completionCharge (no quota deduction)
# -----------------------------------------------------------------------------

TEST_ID="AC3.1"
TEST_DESC="Premium - fast-model billing"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_ID: $TEST_DESC"
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
check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_ID="AC3.2"
TEST_DESC="Premium - reasoning-model billing"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_ID: $TEST_DESC"
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
check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_ID="AC3.3"
TEST_DESC="Premium - Response has modelId"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_ID: $TEST_DESC"
print_expected "HTTP $EXPECTED with modelId='reasoning-model' in response"
echo -e "${YELLOW}Verify modelId field is present in Premium plan response${NC}"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-PREMIUM", "modelId": "reasoning-model", "promptTokens": 1000, "completionTokens": 1000}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_ID="AC3.4"
TEST_DESC="Premium - Zero tokens submission"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_ID: $TEST_DESC"
print_expected "HTTP $EXPECTED with totalCharge=0, promptCharge=0, completionCharge=0"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-PREMIUM", "modelId": "fast-model", "promptTokens": 0, "completionTokens": 0}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"

# =============================================================================
# RESPONSE STRUCTURE VALIDATION
# =============================================================================

# -----------------------------------------------------------------------------
TEST_ID="STRUCT1"
TEST_DESC="Standard - All required fields"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_ID: $TEST_DESC"
print_expected "HTTP $EXPECTED with billId, customerId, modelId, totalTokens, includedTokensUsed, overageTokens, totalCharge, calculatedAt"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-001", "modelId": "fast-model", "promptTokens": 100, "completionTokens": 100}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
TEST_ID="STRUCT2"
TEST_DESC="Premium - Contains charge breakdown"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_ID: $TEST_DESC"
print_expected "HTTP $EXPECTED with promptCharge and completionCharge fields"
print_result
HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
    -H "Content-Type: application/json" \
    -m 10 \
    -d '{"customerId": "CUST-PREMIUM", "modelId": "fast-model", "promptTokens": 100, "completionTokens": 100}')
BODY=$(cat /tmp/response.txt)
check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"

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

# Print structured results table
print_results_table

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
