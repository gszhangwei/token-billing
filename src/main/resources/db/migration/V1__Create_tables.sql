-- customers: basic info only
CREATE TABLE customers (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- pricing_plans: reusable pricing configurations
CREATE TABLE pricing_plans (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    monthly_quota INTEGER NOT NULL,
    overage_rate_per_1k DECIMAL(10, 4) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- customer_subscriptions: customer-plan relationships
CREATE TABLE customer_subscriptions (
    id UUID PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL REFERENCES customers(id),
    plan_id VARCHAR(50) NOT NULL REFERENCES pricing_plans(id),
    effective_from DATE NOT NULL,
    effective_to DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- bills: usage data and calculated billing results
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

CREATE INDEX idx_customer_subscriptions_customer_id ON customer_subscriptions(customer_id);
CREATE INDEX idx_bills_customer_id ON bills(customer_id);
CREATE INDEX idx_bills_calculated_at ON bills(calculated_at);

-- Seed data: pricing plans
INSERT INTO pricing_plans (id, name, monthly_quota, overage_rate_per_1k) VALUES
    ('PLAN-FREE', 'Free Tier', 10000, 0.0300),
    ('PLAN-STARTER', 'Starter', 100000, 0.0200),
    ('PLAN-PRO', 'Professional', 500000, 0.0150),
    ('PLAN-ENTERPRISE', 'Enterprise', 2000000, 0.0100);

-- Seed data: customers
INSERT INTO customers (id, name) VALUES
    ('CUST-001', 'Acme Corp'),
    ('CUST-002', 'TechStart Inc'),
    ('CUST-003', 'Enterprise Solutions Ltd'),
    ('CUST-004', 'No Active Sub Corp'),
    ('CUST-005', 'Multi Sub Corp');

-- Seed data: subscriptions. effective_from uses a far-past date so rows stay
-- active regardless of system clock. CUST-004's effective_to is fixed in the
-- past (also independent of clock) so it remains expired.
INSERT INTO customer_subscriptions (id, customer_id, plan_id, effective_from, effective_to) VALUES
    ('a1b2c3d4-e5f6-7890-abcd-ef1234567890', 'CUST-001', 'PLAN-STARTER',    '2020-01-01', NULL),
    ('b2c3d4e5-f6a7-8901-bcde-f12345678901', 'CUST-002', 'PLAN-FREE',       '2020-01-01', NULL),
    ('c3d4e5f6-a7b8-9012-cdef-123456789012', 'CUST-003', 'PLAN-ENTERPRISE', '2020-01-01', NULL),
    -- CUST-004: expired subscription -> no active subscription (HTTP 409)
    ('d4e5f6a7-a8b9-0123-def0-234567890123', 'CUST-004', 'PLAN-STARTER',    '2020-01-01', '2020-01-02'),
    -- CUST-005: multiple active subscriptions -> data integrity error (HTTP 500)
    ('e5f6a7b8-b9c0-1234-ef01-345678901234', 'CUST-005', 'PLAN-STARTER',    '2020-01-01', NULL),
    ('f6a7b8c9-c0d1-2345-f012-456789012345', 'CUST-005', 'PLAN-PRO',        '2020-01-01', NULL);
