#!/bin/bash
# =============================================================================
# API Test Script — Token Usage Billing
# Endpoint: POST /api/usage
# Usage: ./scripts/test-api.sh [BASE_URL]
#        Default BASE_URL: http://localhost:8080
#
# Requirements: bash, curl (no jq). Timeout -m 10 on every request.
#
# Quota tests (AC3, AC4) reset CUST-001 bills via Docker when available.
# Ensure: docker compose up -d && ./gradlew bootRun
# =============================================================================
#
# TEST CASE OVERVIEW
# | Test ID | Description                    | Customer   | Expected HTTP |
# |---------|--------------------------------|------------|---------------|
# | AC1     | Customer not found             | INVALID    | 404           |
# | AC2a    | Negative prompt tokens         | CUST-001   | 400           |
# | AC2b    | Negative completion tokens     | CUST-001   | 400           |
# | EDGE1   | Zero tokens allowed            | CUST-002   | 201           |
# | EDGE2   | Missing customerId             | (missing)  | 400           |
# | AC5     | Successful bill response       | CUST-002   | 201           |
# | AC3     | Within quota (60k used + 30k)  | CUST-001   | 201           |
# | AC4     | Overage billing (80k + 50k)    | CUST-001   | 201           |
#
# SEED DATA (V1 migration)
# CUST-001 -> PLAN-STARTER: 100,000 quota, $0.02/1K overage
# CUST-002 -> PLAN-FREE:     10,000 quota, $0.03/1K overage
# CUST-003 -> PLAN-ENTERPRISE: 2,000,000 quota, $0.01/1K overage
# =============================================================================

BASE_URL="${1:-http://localhost:8080}"

if [ -t 1 ]; then
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    BLUE='\033[0;34m'
    CYAN='\033[0;36m'
    NC='\033[0m'
else
    RED=''
    GREEN=''
    YELLOW=''
    BLUE=''
    CYAN=''
    NC=''
fi

TESTS_PASSED=0
TESTS_FAILED=0
TESTS_TOTAL=0
declare -a TEST_IDS TEST_DESCRIPTIONS EXPECTED_STATUS ACTUAL_STATUS TEST_RESULTS

print_test_header() {
    echo ""
    echo -e "${BLUE}================================================================${NC}"
    echo -e "${BLUE}TEST: $1${NC}"
    echo -e "${BLUE}================================================================${NC}"
}

print_expected() {
    echo -e "${YELLOW}Expected: $1${NC}"
}

print_result() {
    echo -e "${GREEN}Response:${NC}"
}

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
        echo -e "${GREEN}PASSED${NC} [HTTP $actual_status]"
        TESTS_PASSED=$((TESTS_PASSED + 1))
        record_result "$test_id" "$test_desc" "$expected_status" "$actual_status" "PASS"
    else
        echo -e "${RED}FAILED${NC} [HTTP $actual_status, expected $expected_status]"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        record_result "$test_id" "$test_desc" "$expected_status" "$actual_status" "FAIL"
    fi
    echo ""
}

# Fail an already-passed HTTP check when response body lacks expected content
check_body_contains() {
    local test_id="$1"
    local pattern="$2"
    local body="$3"

    if echo "$body" | grep -Eq "$pattern"; then
        echo -e "${GREEN}Body check PASSED${NC} (pattern: $pattern)"
        return 0
    fi

    echo -e "${RED}Body check FAILED${NC} (expected pattern: $pattern)"
    TESTS_PASSED=$((TESTS_PASSED - 1))
    TESTS_FAILED=$((TESTS_FAILED + 1))
    for i in "${!TEST_IDS[@]}"; do
        if [ "${TEST_IDS[$i]}" = "$test_id" ] && [ "${TEST_RESULTS[$i]}" = "PASS" ]; then
            TEST_RESULTS[$i]="FAIL"
            break
        fi
    done
    return 1
}

post_usage() {
    local json_body="$1"
    HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
        -H "Content-Type: application/json" \
        -m 10 \
        -d "$json_body")
    BODY=$(cat /tmp/response.txt)
}

reset_cust001_bills() {
    if command -v docker >/dev/null 2>&1; then
        docker compose exec -T postgres psql -U postgres -d token_billing \
            -c "DELETE FROM bills WHERE customer_id='CUST-001';" >/dev/null 2>&1 \
            && echo -e "${CYAN}Reset CUST-001 bills for quota test${NC}"
    fi
}

print_results_table() {
    echo ""
    echo -e "${CYAN}TEST RESULTS SUMMARY${NC}"
    printf "%-10s %-32s %-10s %-10s %-8s\n" "Test ID" "Description" "Expected" "Actual" "Result"
    for i in "${!TEST_IDS[@]}"; do
        local color="${GREEN}"
        [ "${TEST_RESULTS[$i]}" = "FAIL" ] && color="${RED}"
        printf "%-10s %-32s %-10s %-10s ${color}%-8s${NC}\n" \
            "${TEST_IDS[$i]}" \
            "${TEST_DESCRIPTIONS[$i]:0:32}" \
            "${EXPECTED_STATUS[$i]}" \
            "${ACTUAL_STATUS[$i]}" \
            "${TEST_RESULTS[$i]}"
    done
}

# =============================================================================
# AC1: Customer not found -> 404
# =============================================================================
TEST_ID="AC1"
TEST_DESC="Customer not found"
EXPECTED="404"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_ID: $TEST_DESC"
print_expected "HTTP $EXPECTED, message: Customer not found"
print_result
post_usage '{"customerId":"NON-EXISTENT","promptTokens":1000,"completionTokens":500}'
check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"
check_body_contains "$TEST_ID" '"message":"Customer not found"' "$BODY"

# =============================================================================
# AC2a: Negative prompt tokens -> 400
# =============================================================================
TEST_ID="AC2a"
TEST_DESC="Negative prompt tokens"
EXPECTED="400"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_ID: $TEST_DESC"
print_expected "HTTP $EXPECTED, message: Token count cannot be negative"
print_result
post_usage '{"customerId":"CUST-001","promptTokens":-1,"completionTokens":500}'
check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"
check_body_contains "$TEST_ID" '"message":"Token count cannot be negative"' "$BODY"

# =============================================================================
# AC2b: Negative completion tokens -> 400
# =============================================================================
TEST_ID="AC2b"
TEST_DESC="Negative completion tokens"
EXPECTED="400"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_ID: $TEST_DESC"
print_expected "HTTP $EXPECTED, message: Token count cannot be negative"
print_result
post_usage '{"customerId":"CUST-001","promptTokens":100,"completionTokens":-5}'
check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"
check_body_contains "$TEST_ID" '"message":"Token count cannot be negative"' "$BODY"

# =============================================================================
# EDGE1: Zero tokens -> 201
# =============================================================================
TEST_ID="EDGE1"
TEST_DESC="Zero token submission"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_ID: $TEST_DESC"
print_expected "HTTP $EXPECTED"
print_result
post_usage '{"customerId":"CUST-002","promptTokens":0,"completionTokens":0}'
check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"
check_body_contains "$TEST_ID" '"totalTokens":0' "$BODY"

# =============================================================================
# EDGE2: Missing customerId -> 400
# =============================================================================
TEST_ID="EDGE2"
TEST_DESC="Missing customerId"
EXPECTED="400"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_ID: $TEST_DESC"
print_expected "HTTP $EXPECTED"
print_result
post_usage '{"promptTokens":100,"completionTokens":50}'
check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"

# =============================================================================
# AC5: Successful 201 with bill fields
# =============================================================================
TEST_ID="AC5"
TEST_DESC="Successful bill response"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_ID: $TEST_DESC"
print_expected "HTTP $EXPECTED with id, customerId, totalTokens, includedTokensUsed, overageTokens, totalCharge, calculatedAt"
print_result
post_usage '{"customerId":"CUST-002","promptTokens":1000,"completionTokens":500}'
check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"
check_body_contains "$TEST_ID" '"customerId":"CUST-002"' "$BODY"
check_body_contains "$TEST_ID" '"totalTokens":1500' "$BODY"
check_body_contains "$TEST_ID" '"includedTokensUsed":' "$BODY"
check_body_contains "$TEST_ID" '"overageTokens":' "$BODY"
check_body_contains "$TEST_ID" '"totalCharge":' "$BODY"
check_body_contains "$TEST_ID" '"calculatedAt":' "$BODY"
check_body_contains "$TEST_ID" '"id":' "$BODY"

# =============================================================================
# AC3: Within quota — 60,000 used then submit 30,000
# =============================================================================
reset_cust001_bills
TEST_ID="AC3-setup"
TEST_DESC="Seed 60000 tokens used"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_ID: $TEST_DESC"
print_expected "HTTP $EXPECTED"
print_result
post_usage '{"customerId":"CUST-001","promptTokens":60000,"completionTokens":0}'
check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"

TEST_ID="AC3"
TEST_DESC="Bill within included quota"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_ID: $TEST_DESC"
print_expected "HTTP $EXPECTED, included 30000, overage 0, charge 0.00"
print_result
post_usage '{"customerId":"CUST-001","promptTokens":30000,"completionTokens":0}'
check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"
check_body_contains "$TEST_ID" '"totalTokens":30000' "$BODY"
check_body_contains "$TEST_ID" '"includedTokensUsed":30000' "$BODY"
check_body_contains "$TEST_ID" '"overageTokens":0' "$BODY"
check_body_contains "$TEST_ID" '"totalCharge":0' "$BODY"

# =============================================================================
# AC4: Overage — 80,000 used then submit 50,000 -> charge $0.60
# =============================================================================
reset_cust001_bills
TEST_ID="AC4-setup"
TEST_DESC="Seed 80000 tokens used"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_ID: $TEST_DESC"
print_expected "HTTP $EXPECTED"
print_result
post_usage '{"customerId":"CUST-001","promptTokens":80000,"completionTokens":0}'
check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"

TEST_ID="AC4"
TEST_DESC="Bill with overage charge"
EXPECTED="201"
TESTS_TOTAL=$((TESTS_TOTAL + 1))
print_test_header "$TEST_ID: $TEST_DESC"
print_expected "HTTP $EXPECTED, included 20000, overage 30000, charge 0.60"
print_result
post_usage '{"customerId":"CUST-001","promptTokens":50000,"completionTokens":0}'
check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"
check_body_contains "$TEST_ID" '"totalTokens":50000' "$BODY"
check_body_contains "$TEST_ID" '"includedTokensUsed":20000' "$BODY"
check_body_contains "$TEST_ID" '"overageTokens":30000' "$BODY"
check_body_contains "$TEST_ID" '"totalCharge":0\.6' "$BODY"

# =============================================================================
# CLEANUP & SUMMARY
# =============================================================================
rm -f /tmp/response.txt

echo ""
echo -e "${BLUE}================================================================${NC}"
echo -e "${BLUE}TEST EXECUTION COMPLETE${NC}"
echo -e "${BLUE}================================================================${NC}"
echo "Base URL: ${BASE_URL}"
echo "Finished at: $(date)"
echo ""

print_results_table

echo ""
echo -e "Tests Passed: ${GREEN}${TESTS_PASSED}${NC}"
echo -e "Tests Failed: ${RED}${TESTS_FAILED}${NC}"
echo -e "Total Tests:  ${TESTS_TOTAL}"
echo ""

if [ "$TESTS_TOTAL" -gt 0 ]; then
    PASS_RATE=$((TESTS_PASSED * 100 / TESTS_TOTAL))
    if [ "$TESTS_FAILED" -eq 0 ]; then
        echo -e "${GREEN}All tests passed (${PASS_RATE}%)${NC}"
    else
        echo -e "${RED}Some tests failed (${PASS_RATE}% passed)${NC}"
    fi
fi
echo ""

if [ "$TESTS_FAILED" -gt 0 ]; then
    exit 1
fi
exit 0
