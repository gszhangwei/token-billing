#!/bin/bash
# =============================================================================
# API Test Script — SPDD skeleton
# Copy this file to scripts/test-api.sh and fill in TEST CASES below.
# Usage: ./scripts/test-api.sh [BASE_URL]   (default: http://localhost:8080)
# Requirements: bash, curl only (no jq). Timeout: -m 10 on every request.
# =============================================================================
#
# TEST CASE OVERVIEW (fill before running)
# | Test ID | Description | Input summary | Expected HTTP |
# |---------|-------------|---------------|---------------|
# | AC1     | ...         | ...           | 201           |
#
# =============================================================================

BASE_URL="${1:-http://localhost:8080}"

if [ -t 1 ]; then
    RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
    BLUE='\033[0;34m'; CYAN='\033[0;36m'; NC='\033[0m'
else
    RED=''; GREEN=''; YELLOW=''; BLUE=''; CYAN=''; NC=''
fi

# SEED DATA REFERENCE (document test entities from migrations/fixtures)
# Customers: ...
# Plans: ...

TESTS_PASSED=0
TESTS_FAILED=0
TESTS_TOTAL=0
declare -a TEST_IDS TEST_DESCRIPTIONS EXPECTED_STATUS ACTUAL_STATUS TEST_RESULTS

print_test_header() {
    echo ""
    echo -e "${BLUE}TEST: $1${NC}"
}

print_expected() { echo -e "${YELLOW}Expected: $1${NC}"; }
print_result() { echo -e "${GREEN}Response:${NC}"; }

record_result() {
    TEST_IDS+=("$1"); TEST_DESCRIPTIONS+=("$2")
    EXPECTED_STATUS+=("$3"); ACTUAL_STATUS+=("$4"); TEST_RESULTS+=("$5")
}

check_result() {
    local test_id="$1" test_desc="$2" expected_status="$3" actual_status="$4" body="$5"
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

print_results_table() {
    echo ""
    echo -e "${CYAN}TEST RESULTS SUMMARY${NC}"
    printf "%-10s %-30s %-10s %-10s %-8s\n" "Test ID" "Description" "Expected" "Actual" "Result"
    for i in "${!TEST_IDS[@]}"; do
        local color="${GREEN}"
        [ "${TEST_RESULTS[$i]}" = "FAIL" ] && color="${RED}"
        printf "%-10s %-30s %-10s %-10s ${color}%-8s${NC}\n" \
            "${TEST_IDS[$i]}" "${TEST_DESCRIPTIONS[$i]:0:30}" \
            "${EXPECTED_STATUS[$i]}" "${ACTUAL_STATUS[$i]}" "${TEST_RESULTS[$i]}"
    done
}

# -----------------------------------------------------------------------------
# TEST CASES — add one curl block per scenario (template below)
# -----------------------------------------------------------------------------
# TEST_ID="AC1"; TEST_DESC="Happy path"; EXPECTED="201"; TESTS_TOTAL=$((TESTS_TOTAL + 1))
# print_test_header "$TEST_ID: $TEST_DESC"
# print_expected "HTTP $EXPECTED"; print_result
# HTTP_CODE=$(curl -s -o /tmp/response.txt -w "%{http_code}" -X POST "${BASE_URL}/api/usage" \
#     -H "Content-Type: application/json" -m 10 \
#     -d '{"customerId":"CUST-001","promptTokens":1000,"completionTokens":500}')
# BODY=$(cat /tmp/response.txt)
# check_result "$TEST_ID" "$TEST_DESC" "$EXPECTED" "$HTTP_CODE" "$BODY"

# -----------------------------------------------------------------------------
# CLEANUP & SUMMARY
# -----------------------------------------------------------------------------
rm -f /tmp/response.txt
echo ""
print_results_table
echo "Passed: ${TESTS_PASSED}  Failed: ${TESTS_FAILED}  Total: ${TESTS_TOTAL}"
[ "$TESTS_FAILED" -gt 0 ] && exit 1
exit 0
