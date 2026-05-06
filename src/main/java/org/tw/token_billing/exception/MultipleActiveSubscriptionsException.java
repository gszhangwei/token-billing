package org.tw.token_billing.exception;

public class MultipleActiveSubscriptionsException extends RuntimeException {

    private final String customerId;

    public MultipleActiveSubscriptionsException(String customerId) {
        super("Multiple active subscriptions found for customer: " + customerId);
        this.customerId = customerId;
    }

    public String getCustomerId() {
        return customerId;
    }
}
