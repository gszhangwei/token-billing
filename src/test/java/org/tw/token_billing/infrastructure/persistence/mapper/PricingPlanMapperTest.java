package org.tw.token_billing.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tw.token_billing.domain.PlanType;
import org.tw.token_billing.domain.PricingPlan;
import org.tw.token_billing.infrastructure.persistence.entity.PricingPlanPO;

class PricingPlanMapperTest {

    private PricingPlanMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new PricingPlanMapper();
    }

    @Test
    @DisplayName("Should convert STANDARD string to PlanType.STANDARD enum when mapping to domain")
    void should_convert_plan_type_string_to_enum_when_to_domain_given_standard_plan_type() {
        PricingPlanPO po = PricingPlanPO.builder()
                .id("PLAN-STARTER")
                .name("Starter Plan")
                .planType("STANDARD")
                .monthlyQuota(100000)
                .overageRatePer1k(new BigDecimal("0.02"))
                .createdAt(LocalDateTime.now())
                .build();

        PricingPlan result = mapper.toDomain(po);

        assertThat(result.getPlanType()).isEqualTo(PlanType.STANDARD);
    }

    @Test
    @DisplayName("Should convert PREMIUM string to PlanType.PREMIUM enum when mapping to domain")
    void should_convert_plan_type_string_to_enum_when_to_domain_given_premium_plan_type() {
        PricingPlanPO po = PricingPlanPO.builder()
                .id("PLAN-PREMIUM")
                .name("Premium Plan")
                .planType("PREMIUM")
                .monthlyQuota(0)
                .overageRatePer1k(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .build();

        PricingPlan result = mapper.toDomain(po);

        assertThat(result.getPlanType()).isEqualTo(PlanType.PREMIUM);
    }
}
