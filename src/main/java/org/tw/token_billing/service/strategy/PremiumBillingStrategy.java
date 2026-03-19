package org.tw.token_billing.service.strategy;

import org.springframework.stereotype.Component;
import org.tw.token_billing.domain.Bill;
import org.tw.token_billing.domain.BillingContext;
import org.tw.token_billing.domain.PlanType;

@Component
public class PremiumBillingStrategy implements BillingStrategy {

    @Override
    public Bill calculate(BillingContext context) {
        return Bill.createPremium(
                context.getCustomerId(),
                context.getModelId(),
                context.getPromptTokens(),
                context.getCompletionTokens(),
                context.getModelPricing().getPromptRatePer1k(),
                context.getModelPricing().getCompletionRatePer1k()
        );
    }

    @Override
    public PlanType supportedPlanType() {
        return PlanType.PREMIUM;
    }
}
