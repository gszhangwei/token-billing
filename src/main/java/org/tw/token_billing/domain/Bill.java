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
    private final String modelId;
    private final Integer promptTokens;
    private final Integer completionTokens;
    private final Integer totalTokens;
    private final Integer includedTokensUsed;
    private final Integer overageTokens;
    private final BigDecimal promptCharge;
    private final BigDecimal completionCharge;
    private final BigDecimal totalCharge;
    private final LocalDateTime calculatedAt;

    @Deprecated
    public static Bill create(String customerId, int promptTokens, int completionTokens,
                              int remainingQuota, BigDecimal overageRatePer1k) {
        return createStandard(customerId, null, promptTokens, completionTokens, remainingQuota, overageRatePer1k);
    }

    public static Bill createStandard(String customerId, String modelId, int promptTokens, int completionTokens,
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
                .modelId(modelId)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(totalTokens)
                .includedTokensUsed(includedTokensUsed)
                .overageTokens(overageTokens)
                .promptCharge(null)
                .completionCharge(null)
                .totalCharge(totalCharge)
                .calculatedAt(LocalDateTime.now(ZoneOffset.UTC))
                .build();
    }

    public static Bill createPremium(String customerId, String modelId, int promptTokens, int completionTokens,
                                     BigDecimal promptRatePer1k, BigDecimal completionRatePer1k) {
        int totalTokens = promptTokens + completionTokens;

        BigDecimal promptCharge = BigDecimal.valueOf(promptTokens)
                .divide(BigDecimal.valueOf(TOKENS_PER_PRICING_UNIT), CALCULATION_PRECISION_SCALE, RoundingMode.HALF_UP)
                .multiply(promptRatePer1k)
                .setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);

        BigDecimal completionCharge = BigDecimal.valueOf(completionTokens)
                .divide(BigDecimal.valueOf(TOKENS_PER_PRICING_UNIT), CALCULATION_PRECISION_SCALE, RoundingMode.HALF_UP)
                .multiply(completionRatePer1k)
                .setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);

        BigDecimal totalCharge = promptCharge.add(completionCharge);

        return Bill.builder()
                .id(UUID.randomUUID())
                .customerId(customerId)
                .modelId(modelId)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(totalTokens)
                .includedTokensUsed(0)
                .overageTokens(0)
                .promptCharge(promptCharge)
                .completionCharge(completionCharge)
                .totalCharge(totalCharge)
                .calculatedAt(LocalDateTime.now(ZoneOffset.UTC))
                .build();
    }
}
