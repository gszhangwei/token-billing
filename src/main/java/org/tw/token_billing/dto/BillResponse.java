package org.tw.token_billing.dto;

import org.tw.token_billing.entity.Bill;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BillResponse(
        UUID id,
        String customerId,
        Integer totalTokens,
        Integer includedTokensUsed,
        Integer overageTokens,
        BigDecimal totalCharge,
        Instant calculatedAt) {

    public static BillResponse from(Bill bill) {
        return new BillResponse(
                bill.getId(),
                bill.getCustomerId(),
                bill.getTotalTokens(),
                bill.getIncludedTokensUsed(),
                bill.getOverageTokens(),
                bill.getTotalCharge(),
                bill.getCalculatedAt());
    }
}
