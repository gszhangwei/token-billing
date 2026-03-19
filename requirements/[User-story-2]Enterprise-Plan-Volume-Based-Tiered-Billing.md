## Background

Building on our newly refactored multi-plan billing engine, we need to introduce complex, volume-based tiered discounts specifically for our high-volume Enterprise customers.

## Business Value

1. **Incentivize Usage**: Encourage high-volume clients through automated tiered discounts.
2. **Competitive Enterprise Offering**: Provide enterprise-grade billing flexibility.

## Scope In

- Enhance the existing billing Strategy/Factory to support a new Enterprise plan type.
- Implement logic to query the customer's accumulated usage for the current month prior to calculating the current request's cost.
- Calculate cross-tier dynamic rates based on specific usage thresholds.

## Scope Out

- Foundational multi-plan refactoring (Completed in Story 1).
- Invoice generation.

## Acceptance Criteria (ACs)

1. **Enterprise Plan with Cross-Tier Calculation (Complex)**
   **Given** an "Enterprise" customer using "fast-model". Tier 1 (0 to 50,000 tokens) is `$0.02/1K`. Tier 2 (above 50,000 tokens) is `$0.01/1K`. The customer has already used 40,000 tokens this month.
   **When** submitting 30,000 tokens (which pushes the total to 70,000)
   **Then** bill splits the usage: 10,000 tokens calculated at Tier 1 rate (`$0.20`), and 20,000 tokens calculated at Tier 2 rate (`$0.20`), for a total charge of `$0.40`.
