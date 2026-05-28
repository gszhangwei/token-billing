package org.tw.token_billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bills")
@Getter
@Setter
@NoArgsConstructor
public class Bill {

    @Id
    private UUID id;

    @Column(name = "customer_id")
    private String customerId;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "included_tokens_used")
    private Integer includedTokensUsed;

    @Column(name = "overage_tokens")
    private Integer overageTokens;

    @Column(name = "total_charge")
    private BigDecimal totalCharge;

    @Column(name = "calculated_at")
    private Instant calculatedAt;
}
