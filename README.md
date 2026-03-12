# Token Billing Service

A Spring Boot application for calculating LLM API token usage bills.

## Requirements

- Java 21
- PostgreSQL (or use Docker Compose)
- Gradle

## Requirements Document

See [Token Usage Billing Story](requirements/token-usage-billing-story.md) for the feature specification to be implemented.

## Quick Start

### 1. Start Database

```bash
docker compose up -d
```

### 2. Run Application

```bash
./gradlew bootRun
```

## Business Rules

1. **Total tokens** = prompt tokens + completion tokens
2. **Included tokens first**: Consume monthly quota before charging overage
3. **Overage calculation**: (overage tokens / 1000) × overage rate per 1K

## Database Schema

### customers
| Column | Type | Description |
|--------|------|-------------|
| id | VARCHAR(50) | Customer ID (PK) |
| name | VARCHAR(100) | Customer name |

### pricing_plans
| Column | Type | Description |
|--------|------|-------------|
| id | VARCHAR(50) | Plan ID (PK) |
| name | VARCHAR(100) | Plan name |
| monthly_quota | INTEGER | Monthly included tokens |
| overage_rate_per_1k | DECIMAL | Rate per 1K overage tokens |

### customer_subscriptions
| Column | Type | Description |
|--------|------|-------------|
| id | UUID | Subscription ID (PK) |
| customer_id | VARCHAR(50) | Customer ID (FK) |
| plan_id | VARCHAR(50) | Plan ID (FK) |
| effective_from | DATE | Subscription start date |
| effective_to | DATE | Subscription end date (nullable) |

### bills
| Column | Type | Description |
|--------|------|-------------|
| id | UUID | Bill ID (PK) |
| customer_id | VARCHAR(50) | Customer ID (FK) |
| prompt_tokens | INTEGER | Prompt tokens submitted |
| completion_tokens | INTEGER | Completion tokens submitted |
| total_tokens | INTEGER | Total tokens (prompt + completion) |
| included_tokens_used | INTEGER | Tokens consumed from quota |
| overage_tokens | INTEGER | Tokens exceeding quota |
| total_charge | DECIMAL | Calculated charge |
| calculated_at | TIMESTAMP | Calculation time |
