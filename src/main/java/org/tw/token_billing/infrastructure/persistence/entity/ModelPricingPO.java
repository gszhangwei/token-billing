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
@Table(name = "model_pricing")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelPricingPO {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "plan_id", length = 50, nullable = false)
    private String planId;

    @Column(name = "model_id", length = 50, nullable = false)
    private String modelId;

    @Column(name = "overage_rate_per_1k", precision = 10, scale = 4)
    private BigDecimal overageRatePer1k;

    @Column(name = "prompt_rate_per_1k", precision = 10, scale = 4)
    private BigDecimal promptRatePer1k;

    @Column(name = "completion_rate_per_1k", precision = 10, scale = 4)
    private BigDecimal completionRatePer1k;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
