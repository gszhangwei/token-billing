package org.tw.token_billing.exception;

public class IdempotencyKeyMismatchException extends RuntimeException {
    private final String customerId;
    private final String idempotencyKey;

    public IdempotencyKeyMismatchException(String customerId, String idempotencyKey) {
        super("Idempotency-Key was reused with a different payload for customer " + customerId);
        this.customerId = customerId;
        this.idempotencyKey = idempotencyKey;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}