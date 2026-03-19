package org.tw.token_billing.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.tw.token_billing.domain.Bill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillResponse {
    private UUID billId;
    private String customerId;
    private String modelId;
    private Integer totalTokens;
    private Integer includedTokensUsed;
    private Integer overageTokens;
    private BigDecimal promptCharge;
    private BigDecimal completionCharge;
    private BigDecimal totalCharge;
    private LocalDateTime calculatedAt;

    public static BillResponse fromBill(Bill bill) {
        return BillResponse.builder()
                .billId(bill.getId())
                .customerId(bill.getCustomerId())
                .modelId(bill.getModelId())
                .totalTokens(bill.getTotalTokens())
                .includedTokensUsed(bill.getIncludedTokensUsed())
                .overageTokens(bill.getOverageTokens())
                .promptCharge(bill.getPromptCharge())
                .completionCharge(bill.getCompletionCharge())
                .totalCharge(bill.getTotalCharge())
                .calculatedAt(bill.getCalculatedAt())
                .build();
    }
}
