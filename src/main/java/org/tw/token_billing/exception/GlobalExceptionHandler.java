package org.tw.token_billing.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.tw.token_billing.dto.ErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String TOKEN_COUNT_NEGATIVE_MESSAGE = "Token count cannot be negative";

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCustomerNotFound(CustomerNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("Customer not found"));
    }

    @ExceptionHandler(NoActiveSubscriptionException.class)
    public ResponseEntity<ErrorResponse> handleNoActiveSubscription(NoActiveSubscriptionException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("No active subscription found"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .filter(TOKEN_COUNT_NEGATIVE_MESSAGE::equals)
                .findFirst()
                .orElseGet(() -> ex.getBindingResult().getFieldErrors().stream()
                        .map(error -> error.getDefaultMessage())
                        .findFirst()
                        .orElse("Validation failed"));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(message));
    }
}
