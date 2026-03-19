package org.tw.token_billing.repository;

import java.util.Optional;

import org.tw.token_billing.domain.ModelPricing;

public interface ModelPricingRepository {
    Optional<ModelPricing> findByPlanIdAndModelId(String planId, String modelId);
}
