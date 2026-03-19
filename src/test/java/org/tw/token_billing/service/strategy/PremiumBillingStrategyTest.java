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

class PremiumBillingStrategyTest {

    private PremiumBillingStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new PremiumBillingStrategy();
    }

    @Test
    @DisplayName("Should return PREMIUM when getting supported plan type")
    void should_return_premium_when_supported_plan_type_given_strategy_instance() {
        PlanType result = strategy.supportedPlanType();

        assertThat(result).isEqualTo(PlanType.PREMIUM);
    }

    @Test
    @DisplayName("Should return bill with split charges when calculating with valid context")
    void should_return_bill_with_split_charges_when_calculate_given_valid_context() {
        ModelPricing modelPricing = ModelPricing.builder()
                .id(UUID.randomUUID())
                .planId("PLAN-PREMIUM")
                .modelId("reasoning-model")
                .promptRatePer1k(new BigDecimal("0.03"))
                .completionRatePer1k(new BigDecimal("0.06"))
                .createdAt(LocalDateTime.now())
                .build();

        BillingContext context = BillingContext.builder()
                .customerId("CUST-PREMIUM")
                .modelId("reasoning-model")
                .promptTokens(10000)
                .completionTokens(20000)
                .remainingQuota(0)
                .modelPricing(modelPricing)
                .build();

        Bill bill = strategy.calculate(context);

        assertThat(bill.getPromptCharge()).isEqualByComparingTo(new BigDecimal("0.30"));
        assertThat(bill.getCompletionCharge()).isEqualByComparingTo(new BigDecimal("1.20"));
        assertThat(bill.getTotalCharge()).isEqualByComparingTo(new BigDecimal("1.50"));
    }

    @Test
    @DisplayName("Should return bill with zero included tokens for premium plan")
    void should_return_bill_with_zero_included_tokens_when_calculate_given_premium_plan() {
        ModelPricing modelPricing = ModelPricing.builder()
                .id(UUID.randomUUID())
                .planId("PLAN-PREMIUM")
                .modelId("fast-model")
                .promptRatePer1k(new BigDecimal("0.01"))
                .completionRatePer1k(new BigDecimal("0.02"))
                .createdAt(LocalDateTime.now())
                .build();

        BillingContext context = BillingContext.builder()
                .customerId("CUST-PREMIUM")
                .modelId("fast-model")
                .promptTokens(5000)
                .completionTokens(3000)
                .remainingQuota(0)
                .modelPricing(modelPricing)
                .build();

        Bill bill = strategy.calculate(context);

        assertThat(bill.getIncludedTokensUsed()).isEqualTo(0);
        assertThat(bill.getOverageTokens()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should return bill with zero charges when tokens are zero")
    void should_return_bill_with_zero_charges_when_calculate_given_zero_tokens() {
        ModelPricing modelPricing = ModelPricing.builder()
                .id(UUID.randomUUID())
                .planId("PLAN-PREMIUM")
                .modelId("fast-model")
                .promptRatePer1k(new BigDecimal("0.01"))
                .completionRatePer1k(new BigDecimal("0.02"))
                .createdAt(LocalDateTime.now())
                .build();

        BillingContext context = BillingContext.builder()
                .customerId("CUST-PREMIUM")
                .modelId("fast-model")
                .promptTokens(0)
                .completionTokens(0)
                .remainingQuota(0)
                .modelPricing(modelPricing)
                .build();

        Bill bill = strategy.calculate(context);

        assertThat(bill.getPromptCharge()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(bill.getCompletionCharge()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(bill.getTotalCharge()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should return bill with only prompt charge when completion tokens are zero")
    void should_return_bill_with_only_prompt_charge_when_calculate_given_zero_completion_tokens() {
        ModelPricing modelPricing = ModelPricing.builder()
                .id(UUID.randomUUID())
                .planId("PLAN-PREMIUM")
                .modelId("reasoning-model")
                .promptRatePer1k(new BigDecimal("0.03"))
                .completionRatePer1k(new BigDecimal("0.06"))
                .createdAt(LocalDateTime.now())
                .build();

        BillingContext context = BillingContext.builder()
                .customerId("CUST-PREMIUM")
                .modelId("reasoning-model")
                .promptTokens(10000)
                .completionTokens(0)
                .remainingQuota(0)
                .modelPricing(modelPricing)
                .build();

        Bill bill = strategy.calculate(context);

        assertThat(bill.getPromptCharge()).isEqualByComparingTo(new BigDecimal("0.30"));
        assertThat(bill.getCompletionCharge()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(bill.getTotalCharge()).isEqualByComparingTo(new BigDecimal("0.30"));
    }

    @Test
    @DisplayName("Should return bill with only completion charge when prompt tokens are zero")
    void should_return_bill_with_only_completion_charge_when_calculate_given_zero_prompt_tokens() {
        ModelPricing modelPricing = ModelPricing.builder()
                .id(UUID.randomUUID())
                .planId("PLAN-PREMIUM")
                .modelId("reasoning-model")
                .promptRatePer1k(new BigDecimal("0.03"))
                .completionRatePer1k(new BigDecimal("0.06"))
                .createdAt(LocalDateTime.now())
                .build();

        BillingContext context = BillingContext.builder()
                .customerId("CUST-PREMIUM")
                .modelId("reasoning-model")
                .promptTokens(0)
                .completionTokens(20000)
                .remainingQuota(0)
                .modelPricing(modelPricing)
                .build();

        Bill bill = strategy.calculate(context);

        assertThat(bill.getPromptCharge()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(bill.getCompletionCharge()).isEqualByComparingTo(new BigDecimal("1.20"));
        assertThat(bill.getTotalCharge()).isEqualByComparingTo(new BigDecimal("1.20"));
    }
}
