package org.tw.token_billing.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UsageRequest(
        @NotNull String customerId,
        @NotNull @Min(value = 0, message = "Token count cannot be negative") Integer promptTokens,
        @NotNull @Min(value = 0, message = "Token count cannot be negative") Integer completionTokens) {
}
