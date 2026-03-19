package org.tw.token_billing.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import org.tw.token_billing.domain.PlanType;
import org.tw.token_billing.domain.PricingPlan;
import org.tw.token_billing.infrastructure.persistence.entity.PricingPlanPO;

@Component
public class PricingPlanMapper {

    public PricingPlan toDomain(PricingPlanPO po) {
        if (po == null) {
            return null;
        }
        return PricingPlan.builder()
                .id(po.getId())
                .name(po.getName())
                .planType(PlanType.valueOf(po.getPlanType()))
                .monthlyQuota(po.getMonthlyQuota())
                .overageRatePer1k(po.getOverageRatePer1k())
                .createdAt(po.getCreatedAt())
                .build();
    }
}
