package org.tw.token_billing.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageRequest {

    @NotNull(message = "Customer ID is required")
    private String customerId;

    @NotNull(message = "Token count cannot be negative")
    @Min(value = 0, message = "Token count cannot be negative")
    private Integer promptTokens;

    @NotNull(message = "Token count cannot be negative")
    @Min(value = 0, message = "Token count cannot be negative")
    private Integer completionTokens;
}
