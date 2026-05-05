package org.tw.token_billing.exception;

/**
 * Exception thrown when a customer is not found in the system.
 */
public class CustomerNotFoundException extends RuntimeException {
    
    private final String customerId;
    
    public CustomerNotFoundException(String customerId) {
        super("Customer '" + customerId + "' does not exist");
        this.customerId = customerId;
    }
    
    public String getCustomerId() {
        return customerId;
    }
}