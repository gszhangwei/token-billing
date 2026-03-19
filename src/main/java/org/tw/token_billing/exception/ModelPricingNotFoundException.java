package org.tw.token_billing.exception;

import lombok.Getter;

@Getter
public class ModelPricingNotFoundException extends RuntimeException {
    private final String planId;
    private final String modelId;

    public ModelPricingNotFoundException(String planId, String modelId) {
        super("Pricing not configured for model: " + modelId);
        this.planId = planId;
        this.modelId = modelId;
    }
}
