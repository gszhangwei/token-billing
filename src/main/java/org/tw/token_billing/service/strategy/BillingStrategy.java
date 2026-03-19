package org.tw.token_billing.service.strategy;

import org.tw.token_billing.domain.Bill;
import org.tw.token_billing.domain.BillingContext;
import org.tw.token_billing.domain.PlanType;

public interface BillingStrategy {
    Bill calculate(BillingContext context);
    PlanType supportedPlanType();
}
