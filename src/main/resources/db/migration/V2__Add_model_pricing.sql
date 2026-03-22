-- Add plan_type to pricing_plans
ALTER TABLE pricing_plans ADD COLUMN plan_type VARCHAR(20) NOT NULL DEFAULT 'STANDARD';

-- Create model_pricing table
CREATE TABLE model_pricing (
    id UUID PRIMARY KEY,
    plan_id VARCHAR(50) NOT NULL REFERENCES pricing_plans(id),
    model_id VARCHAR(50) NOT NULL,
    overage_rate_per_1k DECIMAL(10, 4),
    prompt_rate_per_1k DECIMAL(10, 4),
    completion_rate_per_1k DECIMAL(10, 4),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(plan_id, model_id)
);

-- Add model_id and charge breakdown to bills
ALTER TABLE bills ADD COLUMN model_id VARCHAR(50) NOT NULL DEFAULT 'fast-model';
ALTER TABLE bills ADD COLUMN prompt_charge DECIMAL(10, 2);
ALTER TABLE bills ADD COLUMN completion_charge DECIMAL(10, 2);

-- Create index for model pricing lookup
CREATE INDEX idx_model_pricing_plan_model ON model_pricing(plan_id, model_id);

-- Migrate existing plan overage rates to model_pricing for common models
INSERT INTO model_pricing (id, plan_id, model_id, overage_rate_per_1k)
SELECT gen_random_uuid(), id, 'fast-model', overage_rate_per_1k FROM pricing_plans;

INSERT INTO model_pricing (id, plan_id, model_id, overage_rate_per_1k)
SELECT gen_random_uuid(), id, 'reasoning-model', overage_rate_per_1k FROM pricing_plans;

-- Add a Premium plan for testing
INSERT INTO pricing_plans (id, name, monthly_quota, overage_rate_per_1k, plan_type) VALUES
    ('PLAN-PREMIUM', 'Premium', 0, 0, 'PREMIUM');

-- Add Premium plan model pricing (prompt/completion rates)
INSERT INTO model_pricing (id, plan_id, model_id, prompt_rate_per_1k, completion_rate_per_1k) VALUES
    (gen_random_uuid(), 'PLAN-PREMIUM', 'fast-model', 0.01, 0.02),
    (gen_random_uuid(), 'PLAN-PREMIUM', 'reasoning-model', 0.03, 0.06);

-- Add a Premium customer for testing
INSERT INTO customers (id, name) VALUES
    ('CUST-PREMIUM', 'Premium Test Corp');

-- Add Premium customer subscription
INSERT INTO customer_subscriptions (id, customer_id, plan_id, effective_from) VALUES
    ('d4e5f6a7-b8c9-0123-def0-456789abcdef', 'CUST-PREMIUM', 'PLAN-PREMIUM', '2026-01-01');
