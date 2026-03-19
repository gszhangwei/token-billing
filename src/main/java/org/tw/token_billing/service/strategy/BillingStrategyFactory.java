package org.tw.token_billing.service.strategy;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.tw.token_billing.domain.PlanType;

@Component
public class BillingStrategyFactory {

    private final Map<PlanType, BillingStrategy> strategyMap;

    public BillingStrategyFactory(List<BillingStrategy> strategies) {
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(
                        BillingStrategy::supportedPlanType,
                        Function.identity()
                ));
    }

    public BillingStrategy getStrategy(PlanType planType) {
        BillingStrategy strategy = strategyMap.get(planType);
        if (strategy == null) {
            throw new IllegalArgumentException("No billing strategy found for plan type: " + planType);
        }
        return strategy;
    }
}
