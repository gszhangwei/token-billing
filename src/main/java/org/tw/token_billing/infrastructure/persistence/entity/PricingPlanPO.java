package org.tw.token_billing.infrastructure.persistence.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
@Table(name = "pricing_plans")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingPlanPO {

    @Id
    @Column(name = "id", length = 50)
    private String id;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "monthly_quota", nullable = false)
    private Integer monthlyQuota;

    @Column(name = "overage_rate_per_1k", precision = 10, scale = 4, nullable = false)
    private BigDecimal overageRatePer1k;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
