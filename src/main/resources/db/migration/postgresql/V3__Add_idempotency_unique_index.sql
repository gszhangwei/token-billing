-- Partial unique index: enforce uniqueness only when idempotency_key is set.
-- Multiple bills with NULL key (header absent) for the same customer remain allowed.
-- H2 lacks WHERE-clause partial indexes; see h2/V3 for the portable fallback (NFR-PORT-1).
CREATE UNIQUE INDEX idx_bills_idempotency
    ON bills (customer_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;