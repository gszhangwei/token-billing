package org.tw.token_billing.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ModelPricing {
    private final UUID id;
    private final String planId;
    private final String modelId;
    private final BigDecimal overageRatePer1k;
    private final BigDecimal promptRatePer1k;
    private final BigDecimal completionRatePer1k;
    private final LocalDateTime createdAt;
}
