package org.tw.token_billing.service.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tw.token_billing.domain.PlanType;

class BillingStrategyFactoryTest {

    private BillingStrategyFactory factory;
    private StandardBillingStrategy standardStrategy;
    private PremiumBillingStrategy premiumStrategy;

    @BeforeEach
    void setUp() {
        standardStrategy = new StandardBillingStrategy();
        premiumStrategy = new PremiumBillingStrategy();
        factory = new BillingStrategyFactory(List.of(standardStrategy, premiumStrategy));
    }

    @Test
    @DisplayName("Should return StandardBillingStrategy when getting strategy for STANDARD plan type")
    void should_return_standard_strategy_when_get_strategy_given_standard_plan_type() {
        BillingStrategy result = factory.getStrategy(PlanType.STANDARD);

        assertThat(result).isInstanceOf(StandardBillingStrategy.class);
        assertThat(result.supportedPlanType()).isEqualTo(PlanType.STANDARD);
    }

    @Test
    @DisplayName("Should return PremiumBillingStrategy when getting strategy for PREMIUM plan type")
    void should_return_premium_strategy_when_get_strategy_given_premium_plan_type() {
        BillingStrategy result = factory.getStrategy(PlanType.PREMIUM);

        assertThat(result).isInstanceOf(PremiumBillingStrategy.class);
        assertThat(result.supportedPlanType()).isEqualTo(PlanType.PREMIUM);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when getting strategy for null plan type")
    void should_throw_illegal_argument_exception_when_get_strategy_given_null_plan_type() {
        assertThatThrownBy(() -> factory.getStrategy(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should build strategy map correctly when constructed with list of strategies")
    void should_build_strategy_map_correctly_when_construct_given_list_of_strategies() {
        BillingStrategyFactory newFactory = new BillingStrategyFactory(
                List.of(new StandardBillingStrategy(), new PremiumBillingStrategy())
        );

        BillingStrategy standardResult = newFactory.getStrategy(PlanType.STANDARD);
        BillingStrategy premiumResult = newFactory.getStrategy(PlanType.PREMIUM);

        assertThat(standardResult).isInstanceOf(StandardBillingStrategy.class);
        assertThat(premiumResult).isInstanceOf(PremiumBillingStrategy.class);
    }
}
