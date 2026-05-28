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

@Entity
@Table(name = "pricing_plans")
@Getter
@Setter
@NoArgsConstructor
public class PricingPlan {

    @Id
    private String id;

    private String name;

    @Column(name = "monthly_quota")
    private Integer monthlyQuota;

    @Column(name = "overage_rate_per_1k")
    private BigDecimal overageRatePer1k;

    @Column(name = "created_at")
    private Instant createdAt;
}
