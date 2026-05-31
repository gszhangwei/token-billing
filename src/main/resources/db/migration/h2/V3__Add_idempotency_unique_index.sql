-- H2 2.x does not support partial unique indexes (WHERE clause).
-- Fallback: full UNIQUE constraint on (customer_id, idempotency_key).
-- Divergence vs PostgreSQL (per NFR-PORT-1):
--   PostgreSQL allows multiple NULL idempotency_key per customer.
--   H2 follows the SQL standard (two NULLs are distinct), so practical
--   behaviour matches; documenting explicitly in case of dialect drift.
ALTER TABLE bills ADD CONSTRAINT uq_bills_idempotency
    UNIQUE (customer_id, idempotency_key);