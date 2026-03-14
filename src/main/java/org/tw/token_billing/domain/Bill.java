package org.tw.token_billing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class Bill {
    private static final int TOKENS_PER_PRICING_UNIT = 1000;
    private static final int CALCULATION_PRECISION_SCALE = 10;
    private static final int CURRENCY_SCALE = 2;

    private final UUID id;
    private final String customerId;
    private final Integer promptTokens;
    private final Integer completionTokens;
    private final Integer totalTokens;
    private final Integer includedTokensUsed;
    private final Integer overageTokens;
    private final BigDecimal totalCharge;
    private final LocalDateTime calculatedAt;

    public static Bill create(String customerId, int promptTokens, int completionTokens,
                              int remainingQuota, BigDecimal overageRatePer1k) {
        int totalTokens = promptTokens + completionTokens;
        int includedTokensUsed = Math.min(totalTokens, Math.max(remainingQuota, 0));
        int overageTokens = totalTokens - includedTokensUsed;

        BigDecimal totalCharge = BigDecimal.valueOf(overageTokens)
                .divide(BigDecimal.valueOf(TOKENS_PER_PRICING_UNIT), CALCULATION_PRECISION_SCALE, RoundingMode.HALF_UP)
                .multiply(overageRatePer1k)
                .setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);

        return Bill.builder()
                .id(UUID.randomUUID())
                .customerId(customerId)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(totalTokens)
                .includedTokensUsed(includedTokensUsed)
                .overageTokens(overageTokens)
                .totalCharge(totalCharge)
                .calculatedAt(LocalDateTime.now(ZoneOffset.UTC))
                .build();
    }
}
