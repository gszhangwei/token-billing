package org.tw.token_billing.exception;

public class NoActiveSubscriptionException extends RuntimeException {

    public NoActiveSubscriptionException() {
        super("No active subscription found");
    }
}
