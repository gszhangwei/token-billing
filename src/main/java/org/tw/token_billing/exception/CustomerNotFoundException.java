package org.tw.token_billing.exception;

import lombok.Getter;

@Getter
public class CustomerNotFoundException extends RuntimeException {

    private final String customerId;

    public CustomerNotFoundException(String customerId) {
        super("Customer not found");
        this.customerId = customerId;
    }
}
