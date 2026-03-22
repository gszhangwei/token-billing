package org.tw.token_billing.infrastructure.persistence.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "bills")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillPO {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "customer_id", length = 50, nullable = false)
    private String customerId;

    @Column(name = "model_id", length = 50, nullable = false)
    private String modelId;

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

    @Column(name = "prompt_charge", precision = 10, scale = 2)
    private BigDecimal promptCharge;

    @Column(name = "completion_charge", precision = 10, scale = 2)
    private BigDecimal completionCharge;

    @Column(name = "total_charge", precision = 10, scale = 2, nullable = false)
    private BigDecimal totalCharge;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;
}
