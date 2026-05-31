package org.tw.token_billing.exception;

public class ConcurrentBillingException extends RuntimeException {
    private final String customerId;

    public ConcurrentBillingException(String customerId) {
        super("Concurrent billing in progress for customer: " + customerId);
        this.customerId = customerId;
    }

    public ConcurrentBillingException(String customerId, Throwable cause) {
        super("Concurrent billing in progress for customer: " + customerId, cause);
        this.customerId = customerId;
    }

    public String getCustomerId() {
        return customerId;
    }
}
