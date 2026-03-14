#!/bin/bash

# Token Billing API Test Script
# Prerequisites: Start the application with `./gradlew bootRun`
# Usage: ./scripts/api-test.sh

BASE_URL="${BASE_URL:-http://localhost:8080}"
API_URL="$BASE_URL/api/usage"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_header() {
    echo ""
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

print_test() {
    echo ""
    echo -e "${YELLOW}--- Test: $1 ---${NC}"
    echo -e "${YELLOW}Expected: $2${NC}"
}

print_response() {
    echo -e "Response:"
    echo "$1" | jq . 2>/dev/null || echo "$1"
}

# Check if jq is installed for pretty printing
if ! command -v jq &> /dev/null; then
    echo "Note: Install 'jq' for prettier JSON output"
fi

print_header "Token Billing API Test Suite"
echo "Base URL: $API_URL"
echo "Seed Data:"
echo "  - CUST-001: PLAN-STARTER (100,000 quota, \$0.02/1k overage)"
echo "  - CUST-002: PLAN-FREE (10,000 quota, \$0.03/1k overage)"
echo "  - CUST-003: PLAN-ENTERPRISE (2,000,000 quota, \$0.01/1k overage)"

# =============================================================================
# SUCCESS SCENARIOS (HTTP 201)
# =============================================================================

print_header "SUCCESS SCENARIOS (HTTP 201 Created)"

# Test 1: Basic usage within quota
print_test "1. Basic usage within quota" "HTTP 201, includedTokensUsed=1500, overageTokens=0, totalCharge=0.00"
response=$(curl -s -w "\n%{http_code}" -X POST "$API_URL" \
    -H "Content-Type: application/json" \
    -d '{
        "customerId": "CUST-001",
        "promptTokens": 1000,
        "completionTokens": 500
    }')
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | sed '$d')
echo "HTTP Status: $http_code"
print_response "$body"

# Test 2: Zero token submission
print_test "2. Zero token submission (edge case)" "HTTP 201, totalTokens=0, totalCharge=0.00"
response=$(curl -s -w "\n%{http_code}" -X POST "$API_URL" \
    -H "Content-Type: application/json" \
    -d '{
        "customerId": "CUST-001",
        "promptTokens": 0,
        "completionTokens": 0
    }')
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | sed '$d')
echo "HTTP Status: $http_code"
print_response "$body"

# Test 3: Large usage (likely to exceed quota for Free tier)
print_test "3. Usage exceeding quota (CUST-002 Free tier: 10,000 quota)" "HTTP 201, overageTokens > 0 if quota exhausted"
response=$(curl -s -w "\n%{http_code}" -X POST "$API_URL" \
    -H "Content-Type: application/json" \
    -d '{
        "customerId": "CUST-002",
        "promptTokens": 8000,
        "completionTokens": 5000
    }')
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | sed '$d')
echo "HTTP Status: $http_code"
print_response "$body"

# Test 4: Enterprise customer with large usage
print_test "4. Enterprise customer large usage" "HTTP 201, likely within quota (2M tokens)"
response=$(curl -s -w "\n%{http_code}" -X POST "$API_URL" \
    -H "Content-Type: application/json" \
    -d '{
        "customerId": "CUST-003",
        "promptTokens": 50000,
        "completionTokens": 50000
    }')
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | sed '$d')
echo "HTTP Status: $http_code"
print_response "$body"

# =============================================================================
# ERROR SCENARIOS - NOT FOUND (HTTP 404)
# =============================================================================

print_header "ERROR SCENARIOS - NOT FOUND (HTTP 404)"

# Test 5: Non-existent customer
print_test "5. Non-existent customer ID" "HTTP 404, message: Customer not found"
response=$(curl -s -w "\n%{http_code}" -X POST "$API_URL" \
    -H "Content-Type: application/json" \
    -d '{
        "customerId": "CUST-INVALID",
        "promptTokens": 1000,
        "completionTokens": 500
    }')
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | sed '$d')
echo "HTTP Status: $http_code"
print_response "$body"

# =============================================================================
# ERROR SCENARIOS - VALIDATION (HTTP 400)
# =============================================================================

print_header "ERROR SCENARIOS - VALIDATION (HTTP 400)"

# Test 6: Missing customerId
print_test "6. Missing customerId" "HTTP 400, message: Customer ID is required"
response=$(curl -s -w "\n%{http_code}" -X POST "$API_URL" \
    -H "Content-Type: application/json" \
    -d '{
        "promptTokens": 1000,
        "completionTokens": 500
    }')
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | sed '$d')
echo "HTTP Status: $http_code"
print_response "$body"

# Test 7: Null customerId
print_test "7. Null customerId" "HTTP 400, message: Customer ID is required"
response=$(curl -s -w "\n%{http_code}" -X POST "$API_URL" \
    -H "Content-Type: application/json" \
    -d '{
        "customerId": null,
        "promptTokens": 1000,
        "completionTokens": 500
    }')
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | sed '$d')
echo "HTTP Status: $http_code"
print_response "$body"

# Test 8: Negative promptTokens
print_test "8. Negative promptTokens" "HTTP 400, message: Token count cannot be negative"
response=$(curl -s -w "\n%{http_code}" -X POST "$API_URL" \
    -H "Content-Type: application/json" \
    -d '{
        "customerId": "CUST-001",
        "promptTokens": -100,
        "completionTokens": 500
    }')
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | sed '$d')
echo "HTTP Status: $http_code"
print_response "$body"

# Test 9: Negative completionTokens
print_test "9. Negative completionTokens" "HTTP 400, message: Token count cannot be negative"
response=$(curl -s -w "\n%{http_code}" -X POST "$API_URL" \
    -H "Content-Type: application/json" \
    -d '{
        "customerId": "CUST-001",
        "promptTokens": 1000,
        "completionTokens": -500
    }')
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | sed '$d')
echo "HTTP Status: $http_code"
print_response "$body"

# Test 10: Missing promptTokens
print_test "10. Missing promptTokens" "HTTP 400, validation error"
response=$(curl -s -w "\n%{http_code}" -X POST "$API_URL" \
    -H "Content-Type: application/json" \
    -d '{
        "customerId": "CUST-001",
        "completionTokens": 500
    }')
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | sed '$d')
echo "HTTP Status: $http_code"
print_response "$body"

# Test 11: Missing completionTokens
print_test "11. Missing completionTokens" "HTTP 400, validation error"
response=$(curl -s -w "\n%{http_code}" -X POST "$API_URL" \
    -H "Content-Type: application/json" \
    -d '{
        "customerId": "CUST-001",
        "promptTokens": 1000
    }')
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | sed '$d')
echo "HTTP Status: $http_code"
print_response "$body"

# Test 12: Empty request body
print_test "12. Empty request body" "HTTP 400, validation error"
response=$(curl -s -w "\n%{http_code}" -X POST "$API_URL" \
    -H "Content-Type: application/json" \
    -d '{}')
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | sed '$d')
echo "HTTP Status: $http_code"
print_response "$body"

# =============================================================================
# QUOTA EXHAUSTION SCENARIO
# =============================================================================

print_header "QUOTA EXHAUSTION SCENARIO"

# Test 13-15: Multiple submissions to exhaust Free tier quota
print_test "13-15. Multiple submissions to exhaust Free tier quota (10,000 tokens)" "Observe overageTokens increasing"

echo -e "\n${YELLOW}Submission 1/3 (5000 tokens):${NC}"
response=$(curl -s -w "\n%{http_code}" -X POST "$API_URL" \
    -H "Content-Type: application/json" \
    -d '{
        "customerId": "CUST-002",
        "promptTokens": 3000,
        "completionTokens": 2000
    }')
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | sed '$d')
echo "HTTP Status: $http_code"
print_response "$body"

echo -e "\n${YELLOW}Submission 2/3 (5000 tokens):${NC}"
response=$(curl -s -w "\n%{http_code}" -X POST "$API_URL" \
    -H "Content-Type: application/json" \
    -d '{
        "customerId": "CUST-002",
        "promptTokens": 3000,
        "completionTokens": 2000
    }')
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | sed '$d')
echo "HTTP Status: $http_code"
print_response "$body"

echo -e "\n${YELLOW}Submission 3/3 (5000 tokens - should show overage):${NC}"
response=$(curl -s -w "\n%{http_code}" -X POST "$API_URL" \
    -H "Content-Type: application/json" \
    -d '{
        "customerId": "CUST-002",
        "promptTokens": 3000,
        "completionTokens": 2000
    }')
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | sed '$d')
echo "HTTP Status: $http_code"
print_response "$body"

# =============================================================================
# SUMMARY
# =============================================================================

print_header "TEST SUMMARY"
echo "Test scenarios executed:"
echo "  - Success: Basic usage, zero tokens, large usage, enterprise usage"
echo "  - 404: Non-existent customer"
echo "  - 400: Missing/null customerId, negative tokens, missing required fields"
echo "  - Quota: Multiple submissions to verify quota tracking"
echo ""
echo -e "${GREEN}Script completed. Review the responses above.${NC}"
echo ""
echo "Note: To reset the database and re-run tests, restart the application."
