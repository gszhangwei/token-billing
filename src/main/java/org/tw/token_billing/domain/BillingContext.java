package org.tw.token_billing.domain;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BillingContext {
    private final String customerId;
    private final String modelId;
    private final int promptTokens;
    private final int completionTokens;
    private final int remainingQuota;
    private final ModelPricing modelPricing;
}
