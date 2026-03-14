package org.tw.token_billing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tw.token_billing.infrastructure.persistence.entity.PricingPlanPO;

public interface SpringDataPricingPlanRepository extends JpaRepository<PricingPlanPO, String> {
}
