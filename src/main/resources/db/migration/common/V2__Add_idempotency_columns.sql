-- Add idempotency_key and created_at columns to bills table
ALTER TABLE bills
    ADD COLUMN idempotency_key VARCHAR(255),
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX idx_bills_created_at ON bills (created_at);