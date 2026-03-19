package org.tw.token_billing.service.strategy;

import org.springframework.stereotype.Component;
import org.tw.token_billing.domain.Bill;
import org.tw.token_billing.domain.BillingContext;
import org.tw.token_billing.domain.PlanType;

@Component
public class StandardBillingStrategy implements BillingStrategy {

    @Override
    public Bill calculate(BillingContext context) {
        return Bill.createStandard(
                context.getCustomerId(),
                context.getModelId(),
                context.getPromptTokens(),
                context.getCompletionTokens(),
                context.getRemainingQuota(),
                context.getModelPricing().getOverageRatePer1k()
        );
    }

    @Override
    public PlanType supportedPlanType() {
        return PlanType.STANDARD;
    }
}
