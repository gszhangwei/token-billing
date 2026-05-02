package org.tw.token_billing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class BillResponse {

    private UUID billId;
    private String customerId;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Integer tokensFromQuota;
    private Integer overageTokens;
    private BigDecimal totalCharge;
    private String currency;
    private Instant calculatedAt;

    public BillResponse() {}

    public BillResponse(UUID billId, String customerId, Integer promptTokens, Integer completionTokens,
                       Integer totalTokens, Integer tokensFromQuota, Integer overageTokens,
                       BigDecimal totalCharge, String currency, Instant calculatedAt) {
        this.billId = billId;
        this.customerId = customerId;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.tokensFromQuota = tokensFromQuota;
        this.overageTokens = overageTokens;
        this.totalCharge = totalCharge;
        this.currency = currency;
        this.calculatedAt = calculatedAt;
    }

    public UUID getBillId() { return billId; }
    public void setBillId(UUID billId) { this.billId = billId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public Integer getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Integer promptTokens) { this.promptTokens = promptTokens; }

    public Integer getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Integer completionTokens) { this.completionTokens = completionTokens; }

    public Integer getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Integer totalTokens) { this.totalTokens = totalTokens; }

    public Integer getTokensFromQuota() { return tokensFromQuota; }
    public void setTokensFromQuota(Integer tokensFromQuota) { this.tokensFromQuota = tokensFromQuota; }

    public Integer getOverageTokens() { return overageTokens; }
    public void setOverageTokens(Integer overageTokens) { this.overageTokens = overageTokens; }

    public BigDecimal getTotalCharge() { return totalCharge; }
    public void setTotalCharge(BigDecimal totalCharge) { this.totalCharge = totalCharge; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Instant getCalculatedAt() { return calculatedAt; }
    public void setCalculatedAt(Instant calculatedAt) { this.calculatedAt = calculatedAt; }
}