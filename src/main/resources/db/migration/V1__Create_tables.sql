CREATE TABLE customers (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    monthly_quota INTEGER NOT NULL DEFAULT 0,
    used_tokens_this_month INTEGER NOT NULL DEFAULT 0,
    overage_rate_per_1k DECIMAL(10, 4) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE bills (
    id UUID PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL REFERENCES customers(id),
    prompt_tokens INTEGER NOT NULL,
    completion_tokens INTEGER NOT NULL,
    total_tokens INTEGER NOT NULL,
    included_tokens_used INTEGER NOT NULL,
    overage_tokens INTEGER NOT NULL,
    total_charge DECIMAL(10, 2) NOT NULL,
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_bills_customer_id ON bills(customer_id);
CREATE INDEX idx_bills_calculated_at ON bills(calculated_at);
