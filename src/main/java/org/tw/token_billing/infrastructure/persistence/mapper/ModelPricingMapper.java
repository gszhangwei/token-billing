package org.tw.token_billing.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import org.tw.token_billing.domain.ModelPricing;
import org.tw.token_billing.infrastructure.persistence.entity.ModelPricingPO;

@Component
public class ModelPricingMapper {

    public ModelPricing toDomain(ModelPricingPO po) {
        if (po == null) {
            return null;
        }
        return ModelPricing.builder()
                .id(po.getId())
                .planId(po.getPlanId())
                .modelId(po.getModelId())
                .overageRatePer1k(po.getOverageRatePer1k())
                .promptRatePer1k(po.getPromptRatePer1k())
                .completionRatePer1k(po.getCompletionRatePer1k())
                .createdAt(po.getCreatedAt())
                .build();
    }
}
