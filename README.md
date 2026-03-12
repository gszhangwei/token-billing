# Token Billing Service

A Spring Boot application for calculating LLM API token usage bills.

## Requirements

- Java 21
- PostgreSQL (or use Docker Compose)
- Gradle

## Quick Start

### 1. Start Database

```bash
docker compose up -d
```

### 2. Run Application

```bash
./gradlew bootRun
```

### 3. Test API

```bash
# Submit usage record
curl -X POST http://localhost:8080/api/usage \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-001",
    "promptTokens": 35000,
    "completionTokens": 15000
  }'
```

## Project Structure

```
src/main/java/org/tw/token_billing/
├── TokenBillingApplication.java    # Main application
├── controller/                     # REST controllers
├── service/                        # Business logic
├── domain/                         # Domain entities
├── dto/                            # Request/Response DTOs
├── repository/                     # Data access
└── exception/                      # Custom exceptions
```

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | /api/usage | Submit token usage and get calculated bill |

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
| monthly_quota | INTEGER | Monthly included tokens |
| used_tokens_this_month | INTEGER | Current month usage |
| overage_rate_per_1k | DECIMAL | Rate per 1K overage tokens |

### bills
| Column | Type | Description |
|--------|------|-------------|
| id | UUID | Bill ID (PK) |
| customer_id | VARCHAR(50) | Customer ID (FK) |
| total_tokens | INTEGER | Total tokens used |
| included_tokens_used | INTEGER | Tokens consumed from quota |
| overage_tokens | INTEGER | Tokens exceeding quota |
| total_charge | DECIMAL | Calculated charge |
| calculated_at | TIMESTAMP | Calculation time |
