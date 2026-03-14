package org.tw.token_billing.exception;

import lombok.Getter;

@Getter
public class NoActiveSubscriptionException extends RuntimeException {

    private final String customerId;

    public NoActiveSubscriptionException(String customerId) {
        super("No active subscription found");
        this.customerId = customerId;
    }
}
