package org.tw.token_billing.service.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tw.token_billing.domain.Bill;
import org.tw.token_billing.domain.BillingContext;
import org.tw.token_billing.domain.ModelPricing;
import org.tw.token_billing.domain.PlanType;

class StandardBillingStrategyTest {

    private StandardBillingStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new StandardBillingStrategy();
    }

    @Test
    @DisplayName("Should return STANDARD when getting supported plan type")
    void should_return_standard_when_supported_plan_type_given_strategy_instance() {
        PlanType result = strategy.supportedPlanType();

        assertThat(result).isEqualTo(PlanType.STANDARD);
    }

    @Test
    @DisplayName("Should return bill with all tokens included when usage is within quota")
    void should_return_bill_with_all_tokens_included_when_calculate_given_usage_within_quota() {
        ModelPricing modelPricing = ModelPricing.builder()
                .id(UUID.randomUUID())
                .planId("PLAN-STARTER")
                .modelId("fast-model")
                .overageRatePer1k(new BigDecimal("0.02"))
                .createdAt(LocalDateTime.now())
                .build();

        BillingContext context = BillingContext.builder()
                .customerId("CUST-001")
                .modelId("fast-model")
                .promptTokens(1000)
                .completionTokens(500)
                .remainingQuota(10000)
                .modelPricing(modelPricing)
                .build();

        Bill bill = strategy.calculate(context);

        assertThat(bill.getIncludedTokensUsed()).isEqualTo(1500);
        assertThat(bill.getOverageTokens()).isEqualTo(0);
        assertThat(bill.getTotalCharge()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(bill.getModelId()).isEqualTo("fast-model");
    }

    @Test
    @DisplayName("Should return bill with overage when usage exceeds quota")
    void should_return_bill_with_overage_when_calculate_given_usage_exceeds_quota() {
        ModelPricing modelPricing = ModelPricing.builder()
                .id(UUID.randomUUID())
                .planId("PLAN-FREE")
                .modelId("fast-model")
                .overageRatePer1k(new BigDecimal("0.02"))
                .createdAt(LocalDateTime.now())
                .build();

        BillingContext context = BillingContext.builder()
                .customerId("CUST-001")
                .modelId("fast-model")
                .promptTokens(8000)
                .completionTokens(5000)
                .remainingQuota(10000)
                .modelPricing(modelPricing)
                .build();

        Bill bill = strategy.calculate(context);

        assertThat(bill.getIncludedTokensUsed()).isEqualTo(10000);
        assertThat(bill.getOverageTokens()).isEqualTo(3000);
        assertThat(bill.getTotalCharge()).isEqualByComparingTo(new BigDecimal("0.06"));
    }

    @Test
    @DisplayName("Should return bill with all overage when remaining quota is zero")
    void should_return_bill_with_all_overage_when_calculate_given_zero_remaining_quota() {
        ModelPricing modelPricing = ModelPricing.builder()
                .id(UUID.randomUUID())
                .planId("PLAN-STARTER")
                .modelId("fast-model")
                .overageRatePer1k(new BigDecimal("0.02"))
                .createdAt(LocalDateTime.now())
                .build();

        BillingContext context = BillingContext.builder()
                .customerId("CUST-001")
                .modelId("fast-model")
                .promptTokens(1000)
                .completionTokens(500)
                .remainingQuota(0)
                .modelPricing(modelPricing)
                .build();

        Bill bill = strategy.calculate(context);

        assertThat(bill.getIncludedTokensUsed()).isEqualTo(0);
        assertThat(bill.getOverageTokens()).isEqualTo(1500);
        assertThat(bill.getTotalCharge()).isEqualByComparingTo(new BigDecimal("0.03"));
    }

    @Test
    @DisplayName("Should return bill with null prompt and completion charges for standard plan")
    void should_return_bill_with_null_prompt_completion_charges_when_calculate_given_standard_plan() {
        ModelPricing modelPricing = ModelPricing.builder()
                .id(UUID.randomUUID())
                .planId("PLAN-STARTER")
                .modelId("fast-model")
                .overageRatePer1k(new BigDecimal("0.02"))
                .createdAt(LocalDateTime.now())
                .build();

        BillingContext context = BillingContext.builder()
                .customerId("CUST-001")
                .modelId("fast-model")
                .promptTokens(1000)
                .completionTokens(500)
                .remainingQuota(10000)
                .modelPricing(modelPricing)
                .build();

        Bill bill = strategy.calculate(context);

        assertThat(bill.getPromptCharge()).isNull();
        assertThat(bill.getCompletionCharge()).isNull();
    }
}
