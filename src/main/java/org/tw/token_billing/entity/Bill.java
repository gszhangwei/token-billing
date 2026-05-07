package org.tw.token_billing.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bills")
public class Bill {

    @Id
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "prompt_tokens", nullable = false)
    private Integer promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private Integer completionTokens;

    @Column(name = "total_tokens", nullable = false)
    private Integer totalTokens;

    @Column(name = "included_tokens_used", nullable = false)
    private Integer includedTokensUsed;

    @Column(name = "overage_tokens", nullable = false)
    private Integer overageTokens;

    @Column(name = "total_charge", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalCharge;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public Bill() {}

    public Bill(UUID id, Customer customer, Integer promptTokens, Integer completionTokens,
              Integer totalTokens, Integer includedTokensUsed, Integer overageTokens,
              BigDecimal totalCharge, Instant calculatedAt) {
        this.id = id;
        this.customer = customer;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.includedTokensUsed = includedTokensUsed;
        this.overageTokens = overageTokens;
        this.totalCharge = totalCharge;
        this.calculatedAt = calculatedAt;
    }

    public Bill(UUID id, Customer customer, Integer promptTokens, Integer completionTokens,
              Integer totalTokens, Integer includedTokensUsed, Integer overageTokens,
              BigDecimal totalCharge, Instant calculatedAt, String idempotencyKey) {
        this.id = id;
        this.customer = customer;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.includedTokensUsed = includedTokensUsed;
        this.overageTokens = overageTokens;
        this.totalCharge = totalCharge;
        this.calculatedAt = calculatedAt;
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }

    public Integer getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Integer promptTokens) { this.promptTokens = promptTokens; }

    public Integer getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Integer completionTokens) { this.completionTokens = completionTokens; }

    public Integer getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Integer totalTokens) { this.totalTokens = totalTokens; }

    public Integer getIncludedTokensUsed() { return includedTokensUsed; }
    public void setIncludedTokensUsed(Integer includedTokensUsed) { this.includedTokensUsed = includedTokensUsed; }

    public Integer getOverageTokens() { return overageTokens; }
    public void setOverageTokens(Integer overageTokens) { this.overageTokens = overageTokens; }

    public BigDecimal getTotalCharge() { return totalCharge; }
    public void setTotalCharge(BigDecimal totalCharge) { this.totalCharge = totalCharge; }

    public Instant getCalculatedAt() { return calculatedAt; }
    public void setCalculatedAt(Instant calculatedAt) { this.calculatedAt = calculatedAt; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public Instant getCreatedAt() { return createdAt; }
}