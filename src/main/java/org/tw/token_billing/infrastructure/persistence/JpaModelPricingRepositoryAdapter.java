package org.tw.token_billing.infrastructure.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.tw.token_billing.domain.ModelPricing;
import org.tw.token_billing.infrastructure.persistence.mapper.ModelPricingMapper;
import org.tw.token_billing.repository.ModelPricingRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JpaModelPricingRepositoryAdapter implements ModelPricingRepository {

    private final SpringDataModelPricingRepository springDataRepository;
    private final ModelPricingMapper mapper;

    @Override
    public Optional<ModelPricing> findByPlanIdAndModelId(String planId, String modelId) {
        return springDataRepository.findByPlanIdAndModelId(planId, modelId)
                .map(mapper::toDomain);
    }
}
