package org.tw.token_billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

public class UsageRequest {

    @NotBlank(message = "Customer ID is required")
    private String customerId;

    @NotNull(message = "Prompt tokens is required")
    @Min(value = 0, message = "Prompt tokens cannot be negative")
    private Integer promptTokens;

    @NotNull(message = "Completion tokens is required")
    @Min(value = 0, message = "Completion tokens cannot be negative")
    private Integer completionTokens;

    public UsageRequest() {}

    public UsageRequest(String customerId, Integer promptTokens, Integer completionTokens) {
        this.customerId = customerId;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
    }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public Integer getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Integer promptTokens) { this.promptTokens = promptTokens; }

    public Integer getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Integer completionTokens) { this.completionTokens = completionTokens; }
}