package org.tw.token_billing.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tw.token_billing.domain.ModelPricing;
import org.tw.token_billing.infrastructure.persistence.entity.ModelPricingPO;
import org.tw.token_billing.infrastructure.persistence.mapper.ModelPricingMapper;

@ExtendWith(MockitoExtension.class)
class JpaModelPricingRepositoryAdapterTest {

    @Mock
    private SpringDataModelPricingRepository springDataRepository;

    @Mock
    private ModelPricingMapper modelPricingMapper;

    @InjectMocks
    private JpaModelPricingRepositoryAdapter adapter;

    @Test
    @DisplayName("Should return ModelPricing when finding by existing plan and model combination")
    void should_return_model_pricing_when_find_by_plan_id_and_model_id_given_existing_combination() {
        String planId = "PLAN-STARTER";
        String modelId = "fast-model";
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        ModelPricingPO po = ModelPricingPO.builder()
                .id(id)
                .planId(planId)
                .modelId(modelId)
                .overageRatePer1k(new BigDecimal("0.02"))
                .createdAt(now)
                .build();

        ModelPricing expectedDomain = ModelPricing.builder()
                .id(id)
                .planId(planId)
                .modelId(modelId)
                .overageRatePer1k(new BigDecimal("0.02"))
                .createdAt(now)
                .build();

        when(springDataRepository.findByPlanIdAndModelId(planId, modelId))
                .thenReturn(Optional.of(po));
        when(modelPricingMapper.toDomain(po)).thenReturn(expectedDomain);

        Optional<ModelPricing> result = adapter.findByPlanIdAndModelId(planId, modelId);

        assertThat(result).isPresent();
        assertThat(result.get().getPlanId()).isEqualTo(planId);
        assertThat(result.get().getModelId()).isEqualTo(modelId);
        verify(springDataRepository).findByPlanIdAndModelId(planId, modelId);
        verify(modelPricingMapper).toDomain(po);
    }

    @Test
    @DisplayName("Should return empty Optional when finding by non-existent plan and model combination")
    void should_return_empty_optional_when_find_by_plan_id_and_model_id_given_non_existent_combination() {
        String planId = "PLAN-STARTER";
        String modelId = "unknown-model";

        when(springDataRepository.findByPlanIdAndModelId(planId, modelId))
                .thenReturn(Optional.empty());

        Optional<ModelPricing> result = adapter.findByPlanIdAndModelId(planId, modelId);

        assertThat(result).isEmpty();
        verify(springDataRepository).findByPlanIdAndModelId(planId, modelId);
        verify(modelPricingMapper, never()).toDomain(org.mockito.ArgumentMatchers.any());
    }
}
