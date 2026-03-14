package org.tw.token_billing.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class CustomerSubscription {
    private final UUID id;
    private final String customerId;
    private final PricingPlan plan;
    private final LocalDate effectiveFrom;
    private final LocalDate effectiveTo;
    private final LocalDateTime createdAt;
}
