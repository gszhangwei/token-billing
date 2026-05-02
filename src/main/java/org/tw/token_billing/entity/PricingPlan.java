package org.tw.token_billing.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "pricing_plans")
public class PricingPlan {

    @Id
    @Column(length = 50)
    private String id;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(name = "monthly_quota", nullable = false)
    private Integer monthlyQuota;

    @Column(name = "overage_rate_per_1k", nullable = false, precision = 10, scale = 4)
    private BigDecimal overageRatePer1k;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public PricingPlan() {}

    public PricingPlan(String id, String name, Integer monthlyQuota, BigDecimal overageRatePer1k, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.monthlyQuota = monthlyQuota;
        this.overageRatePer1k = overageRatePer1k;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getMonthlyQuota() { return monthlyQuota; }
    public void setMonthlyQuota(Integer monthlyQuota) { this.monthlyQuota = monthlyQuota; }

    public BigDecimal getOverageRatePer1k() { return overageRatePer1k; }
    public void setOverageRatePer1k(BigDecimal overageRatePer1k) { this.overageRatePer1k = overageRatePer1k; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}