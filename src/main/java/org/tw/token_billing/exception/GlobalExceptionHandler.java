package org.tw.token_billing.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.stream.Collectors;

/**
 * Global exception handler producing RFC 7807 ProblemDetail responses.
 * Handles all validation errors and transforms them into standard error envelope.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final URI DEFAULT_TYPE = URI.create("about:blank");

    /**
     * Handle constraint violations (from @Validated on method parameters)
     */
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(jakarta.validation.ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .collect(Collectors.joining(", "));
        
        if (detail.contains("customerId")) {
            return createProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Invalid customer ID format",
                "Invalid customer ID format"
            );
        } else if (detail.contains("negative")) {
            return createProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Token count cannot be negative",
                "Token count cannot be negative"
            );
        }
        
        return createProblemDetail(
            HttpStatus.BAD_REQUEST,
            "Invalid request body",
            detail.isEmpty() ? "Invalid request body" : detail
        );
    }

    /**
     * Handle @RequestBody validation failures
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        // Check for specific field errors
        if (ex.getBindingResult().hasFieldErrors("customerId")) {
            String customerIdError = ex.getBindingResult().getFieldError("customerId").getDefaultMessage();
            if (customerIdError != null && (customerIdError.contains("pattern") || customerIdError.contains("size"))) {
                return createProblemDetail(
                    HttpStatus.BAD_REQUEST,
                    "Invalid customer ID format",
                    "Invalid customer ID format"
                );
            }
        }
        
        if (ex.getBindingResult().hasFieldErrors("promptTokens") || 
            ex.getBindingResult().hasFieldErrors("completionTokens")) {
            // Check for negative values
            if (ex.getBindingResult().hasFieldErrors("promptTokens")) {
                String msg = ex.getBindingResult().getFieldError("promptTokens").getDefaultMessage();
                if (msg != null && (msg.contains("positive") || msg.contains("negative"))) {
                    return createProblemDetail(
                        HttpStatus.BAD_REQUEST,
                        "Token count cannot be negative",
                        "Token count cannot be negative"
                    );
                }
            }
            if (ex.getBindingResult().hasFieldErrors("completionTokens")) {
                String msg = ex.getBindingResult().getFieldError("completionTokens").getDefaultMessage();
                if (msg != null && (msg.contains("positive") || msg.contains("negative"))) {
                    return createProblemDetail(
                        HttpStatus.BAD_REQUEST,
                        "Token count cannot be negative",
                        "Token count cannot be negative"
                    );
                }
            }
        }
        
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        
        return createProblemDetail(
            HttpStatus.BAD_REQUEST,
            "Invalid request body",
            detail.isEmpty() ? "Invalid request body" : detail
        );
    }

    /**
     * Handle malformed JSON / unknown properties
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        String message = ex.getMessage();
        
        if (message != null && (message.contains("unknown property") || message.contains("unrecognized"))) {
            return createProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Invalid request body",
                "Invalid request body"
            );
        }
        
        return createProblemDetail(
            HttpStatus.BAD_REQUEST,
            "Invalid request body",
            "Invalid request body"
        );
    }

    /**
     * Handle type mismatch errors
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return createProblemDetail(
            HttpStatus.BAD_REQUEST,
            "Invalid request body",
            "Invalid request body"
        );
    }

    /**
     * Handle all other exceptions as 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        return createProblemDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal server error",
            ex.getMessage()
        );
    }

    /**
     * Handle customer not found exception with RFC 7807 format
     */
    @ExceptionHandler(CustomerNotFoundException.class)
    public ProblemDetail handleCustomerNotFound(CustomerNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            "Customer '" + ex.getCustomerId() + "' does not exist"
        );
        problemDetail.setType(DEFAULT_TYPE);
        problemDetail.setTitle("Customer not found");
        problemDetail.setInstance(URI.create("/api/usage"));
        return problemDetail;
    }

    private ProblemDetail createProblemDetail(HttpStatus status, String title, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatusCode.valueOf(status.value()),
            detail
        );
        problemDetail.setType(DEFAULT_TYPE);
        problemDetail.setTitle(title);
        problemDetail.setInstance(URI.create("/api/usage"));
        return problemDetail;
    }
}