## Background
The LLM API platform charges customers based on token consumption. Customers have monthly included quotas; usage exceeding the quota is billed at an overage rate.

## Business Value
1. **Accurate Billing**: Calculate charges based on actual token consumption.
2. **Quota Management**: Track usage against included quotas.
3. **Revenue Capture**: Bill overage when customers exceed quotas.

## Scope In
* Implement POST /api/usage endpoint for submitting token usage and receiving calculated bills.
* Request fields:
  * Customer ID (required, must exist)
  * Prompt tokens (required, ≥ 0)
  * Completion tokens (required, ≥ 0)
* Calculate bill using customer's monthly quota, current month usage, and overage rate.
* Update customer's current month usage after billing.

## Scope Out
* Customer CRUD operations.
* Historical bill queries.
* Monthly quota reset logic.

## Acceptance Criteria (ACs)
1. Validate Customer ID exists
   **Given** customer ID does not exist
   **When** backend receives request
   **Then** return HTTP 404, message "Customer not found".

2. Validate token counts are non-negative
   **Given** prompt tokens or completion tokens is negative
   **When** backend validates request
   **Then** return HTTP 400, message "Token count cannot be negative".

3. Bill within included quota
   **Given** customer has 100,000 monthly quota and 60,000 tokens used this month
   **When** submitting 30,000 tokens
   **Then** bill shows: 30,000 from quota, 0 overage, $0.00 charge.

4. Bill exceeding included quota
   **Given** customer has 100,000 monthly quota, 80,000 tokens used this month, overage rate $0.02 per 1K tokens
   **When** submitting 50,000 tokens
   **Then** bill shows: 20,000 from quota, 30,000 overage, $0.60 charge.

5. Update customer usage
   **Given** bill calculated for 50,000 tokens
   **When** bill is persisted
   **Then** customer's current month usage increases by 50,000.

6. Successful return
   **Given** valid request
   **When** bill is calculated
   **Then** return HTTP 201 with bill details including: bill ID, customer ID, total tokens, tokens from quota, overage tokens, total charge, and calculation timestamp.
