package org.tw.token_billing.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tw.token_billing.infrastructure.persistence.entity.ModelPricingPO;

public interface SpringDataModelPricingRepository extends JpaRepository<ModelPricingPO, UUID> {
    Optional<ModelPricingPO> findByPlanIdAndModelId(String planId, String modelId);
}
