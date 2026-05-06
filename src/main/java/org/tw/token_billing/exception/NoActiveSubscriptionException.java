package org.tw.token_billing.exception;

public class NoActiveSubscriptionException extends RuntimeException {

    private final String customerId;

    public NoActiveSubscriptionException(String customerId) {
        super("No active subscription found for customer: " + customerId);
        this.customerId = customerId;
    }

    public String getCustomerId() {
        return customerId;
    }
}
