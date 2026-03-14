package org.tw.token_billing.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class PricingPlan {
    private final String id;
    private final String name;
    private final Integer monthlyQuota;
    private final BigDecimal overageRatePer1k;
    private final LocalDateTime createdAt;
}
