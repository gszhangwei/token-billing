package org.tw.token_billing.service;

import org.tw.token_billing.entity.PricingPlan;

record MonthlyQuotaContext(PricingPlan plan, long currentMonthUsage, int remainingQuota) {
}
