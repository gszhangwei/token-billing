package org.tw.token_billing.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import org.tw.token_billing.domain.Bill;
import org.tw.token_billing.infrastructure.persistence.entity.BillPO;

@Component
public class BillMapper {

    public Bill toDomain(BillPO po) {
        if (po == null) {
            return null;
        }
        return Bill.builder()
                .id(po.getId())
                .customerId(po.getCustomerId())
                .modelId(po.getModelId())
                .promptTokens(po.getPromptTokens())
                .completionTokens(po.getCompletionTokens())
                .totalTokens(po.getTotalTokens())
                .includedTokensUsed(po.getIncludedTokensUsed())
                .overageTokens(po.getOverageTokens())
                .promptCharge(po.getPromptCharge())
                .completionCharge(po.getCompletionCharge())
                .totalCharge(po.getTotalCharge())
                .calculatedAt(po.getCalculatedAt())
                .build();
    }

    public BillPO toPO(Bill domain) {
        if (domain == null) {
            return null;
        }
        return BillPO.builder()
                .id(domain.getId())
                .customerId(domain.getCustomerId())
                .modelId(domain.getModelId())
                .promptTokens(domain.getPromptTokens())
                .completionTokens(domain.getCompletionTokens())
                .totalTokens(domain.getTotalTokens())
                .includedTokensUsed(domain.getIncludedTokensUsed())
                .overageTokens(domain.getOverageTokens())
                .promptCharge(domain.getPromptCharge())
                .completionCharge(domain.getCompletionCharge())
                .totalCharge(domain.getTotalCharge())
                .calculatedAt(domain.getCalculatedAt())
                .build();
    }
}
