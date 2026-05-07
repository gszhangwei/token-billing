package org.tw.token_billing.exception;

import jakarta.validation.ConstraintViolation;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.tw.token_billing.exception.CustomerNotFoundException;
import org.tw.token_billing.exception.IdempotencyKeyMismatchException;
import org.tw.token_billing.exception.MultipleActiveSubscriptionsException;
import org.tw.token_billing.exception.NoActiveSubscriptionException;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Global exception handler producing RFC 7807 ProblemDetail responses.
 * Handles all validation errors and transforms them into standard error envelope.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final URI DEFAULT_TYPE = URI.create("about:blank");

    private static final String CUSTOMER_ID_FIELD = "customerId";
    private static final Set<String> TOKEN_FIELDS = Set.of("promptTokens", "completionTokens");
    private static final Set<String> CUSTOMER_ID_FORMAT_CODES = Set.of("Pattern", "Size");
    private static final String NEGATIVE_TOKEN_CODE = "Min";
    private static final String IDEMPOTENCY_KEY_FIELD = "idempotencyKey";

    /**
     * Handle constraint violations (from @Validated on method parameters)
     */
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(jakarta.validation.ConstraintViolationException ex) {
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            String annotation = v.getConstraintDescriptor().getAnnotation()
                    .annotationType().getSimpleName();
            String field = lastPathSegment(v.getPropertyPath().toString());

            if (CUSTOMER_ID_FIELD.equals(field) && CUSTOMER_ID_FORMAT_CODES.contains(annotation)) {
                return createProblemDetail(
                    HttpStatus.BAD_REQUEST,
                    "Invalid customer ID format",
                    "Invalid customer ID format"
                );
            }
            if (TOKEN_FIELDS.contains(field) && NEGATIVE_TOKEN_CODE.equals(annotation)) {
                return createProblemDetail(
                    HttpStatus.BAD_REQUEST,
                    "Token count cannot be negative",
                    "Token count cannot be negative"
                );
            }
            if (IDEMPOTENCY_KEY_FIELD.equals(field) && "Pattern".equals(annotation)) {
                return createProblemDetail(
                    HttpStatus.BAD_REQUEST,
                    "Invalid Idempotency-Key format",
                    "Invalid Idempotency-Key format");
            }
        }

        String detail = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
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
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();

        boolean customerIdFormatViolation = fieldErrors.stream().anyMatch(fe ->
            CUSTOMER_ID_FIELD.equals(fe.getField())
            && hasErrorCode(fe, CUSTOMER_ID_FORMAT_CODES));
        if (customerIdFormatViolation) {
            return createProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Invalid customer ID format",
                "Invalid customer ID format"
            );
        }

        boolean negativeTokenViolation = fieldErrors.stream().anyMatch(fe ->
            TOKEN_FIELDS.contains(fe.getField())
            && hasErrorCode(fe, Set.of(NEGATIVE_TOKEN_CODE)));
        if (negativeTokenViolation) {
            return createProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Token count cannot be negative",
                "Token count cannot be negative"
            );
        }

        String detail = fieldErrors.stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return createProblemDetail(
            HttpStatus.BAD_REQUEST,
            "Invalid request body",
            detail.isEmpty() ? "Invalid request body" : detail
        );
    }

    /**
     * Whether a FieldError was raised by any of the given Bean Validation constraint codes
     * (e.g. "Pattern", "Min"). Spring populates FieldError.getCodes() with entries such as
     * "Pattern.usageRequest.customerId", "Pattern.customerId", "Pattern.java.lang.String", "Pattern".
     */
    private static boolean hasErrorCode(FieldError fe, Set<String> codes) {
        String[] errorCodes = fe.getCodes();
        if (errorCodes == null) {
            return false;
        }
        for (String raw : errorCodes) {
            String head = raw.contains(".") ? raw.substring(0, raw.indexOf('.')) : raw;
            if (codes.contains(head)) {
                return true;
            }
        }
        return false;
    }

    private static String lastPathSegment(String path) {
        int idx = path.lastIndexOf('.');
        return idx < 0 ? path : path.substring(idx + 1);
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

    /**
     * SRS-F-7 / AC6: customer exists but has zero active subscriptions.
     */
    @ExceptionHandler(NoActiveSubscriptionException.class)
    public ProblemDetail handleNoActiveSubscription(NoActiveSubscriptionException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "Customer '" + ex.getCustomerId() + "' has no active subscription"
        );
        problemDetail.setType(DEFAULT_TYPE);
        problemDetail.setTitle("No active subscription");
        problemDetail.setInstance(URI.create("/api/usage"));
        return problemDetail;
    }

    /**
     * SRS-F-7: customer has 2+ active subscriptions; treated as data integrity error.
     */
    @ExceptionHandler(MultipleActiveSubscriptionsException.class)
    public ProblemDetail handleMultipleActiveSubscriptions(MultipleActiveSubscriptionsException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Multiple active subscriptions found for customer '" + ex.getCustomerId() + "'"
        );
        problemDetail.setType(DEFAULT_TYPE);
        problemDetail.setTitle("Data integrity error");
        problemDetail.setInstance(URI.create("/api/usage"));
        return problemDetail;
    }

    @ExceptionHandler(IdempotencyKeyMismatchException.class)
    public ProblemDetail handleIdempotencyMismatch(IdempotencyKeyMismatchException ex) {
        String prefix = ex.getIdempotencyKey()
            .substring(0, Math.min(8, ex.getIdempotencyKey().length()));
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "Idempotency-Key '" + prefix + "...' was reused with a different payload");
        problemDetail.setType(DEFAULT_TYPE);
        problemDetail.setTitle("Idempotency-Key reused with different payload");
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