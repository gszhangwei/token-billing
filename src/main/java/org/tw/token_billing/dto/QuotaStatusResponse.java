package org.tw.token_billing.dto;

import org.tw.token_billing.entity.PricingPlan;

import java.math.BigDecimal;

public record QuotaStatusResponse(
        String customerId,
        Integer monthlyQuota,
        Long tokensUsedThisMonth,
        Integer remainingQuota,
        BigDecimal overageRatePer1k) {

    public static QuotaStatusResponse from(
            String customerId,
            PricingPlan plan,
            long tokensUsedThisMonth,
            int remainingQuota) {
        return new QuotaStatusResponse(
                customerId,
                plan.getMonthlyQuota(),
                tokensUsedThisMonth,
                remainingQuota,
                plan.getOverageRatePer1k());
    }
}
