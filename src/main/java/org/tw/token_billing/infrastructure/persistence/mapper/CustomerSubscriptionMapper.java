package org.tw.token_billing.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import org.tw.token_billing.domain.CustomerSubscription;
import org.tw.token_billing.domain.PricingPlan;
import org.tw.token_billing.infrastructure.persistence.entity.CustomerSubscriptionPO;

@Component
public class CustomerSubscriptionMapper {

    public CustomerSubscription toDomain(CustomerSubscriptionPO po, PricingPlan plan) {
        if (po == null) {
            return null;
        }
        return CustomerSubscription.builder()
                .id(po.getId())
                .customerId(po.getCustomerId())
                .plan(plan)
                .effectiveFrom(po.getEffectiveFrom())
                .effectiveTo(po.getEffectiveTo())
                .createdAt(po.getCreatedAt())
                .build();
    }
}
