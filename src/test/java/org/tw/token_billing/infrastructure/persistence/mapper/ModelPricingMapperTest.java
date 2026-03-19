package org.tw.token_billing.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tw.token_billing.domain.ModelPricing;
import org.tw.token_billing.infrastructure.persistence.entity.ModelPricingPO;

class ModelPricingMapperTest {

    private ModelPricingMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ModelPricingMapper();
    }

    @Test
    @DisplayName("Should map all fields when converting PO to domain")
    void should_map_all_fields_when_to_domain_given_valid_po() {
        UUID id = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();

        ModelPricingPO po = ModelPricingPO.builder()
                .id(id)
                .planId("PLAN-PREMIUM")
                .modelId("reasoning-model")
                .overageRatePer1k(new BigDecimal("0.02"))
                .promptRatePer1k(new BigDecimal("0.03"))
                .completionRatePer1k(new BigDecimal("0.06"))
                .createdAt(createdAt)
                .build();

        ModelPricing result = mapper.toDomain(po);

        assertThat(result.getId()).isEqualTo(id);
        assertThat(result.getPlanId()).isEqualTo("PLAN-PREMIUM");
        assertThat(result.getModelId()).isEqualTo("reasoning-model");
        assertThat(result.getOverageRatePer1k()).isEqualByComparingTo(new BigDecimal("0.02"));
        assertThat(result.getPromptRatePer1k()).isEqualByComparingTo(new BigDecimal("0.03"));
        assertThat(result.getCompletionRatePer1k()).isEqualByComparingTo(new BigDecimal("0.06"));
        assertThat(result.getCreatedAt()).isEqualTo(createdAt);
    }
}
