package org.tw.token_billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tw.token_billing.entity.PricingPlan;

public interface PricingPlanRepository extends JpaRepository<PricingPlan, String> {
}