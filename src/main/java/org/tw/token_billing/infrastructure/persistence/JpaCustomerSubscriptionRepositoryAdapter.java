package org.tw.token_billing.infrastructure.persistence;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.tw.token_billing.domain.CustomerSubscription;
import org.tw.token_billing.domain.PricingPlan;
import org.tw.token_billing.infrastructure.persistence.mapper.CustomerSubscriptionMapper;
import org.tw.token_billing.infrastructure.persistence.mapper.PricingPlanMapper;
import org.tw.token_billing.repository.CustomerSubscriptionRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JpaCustomerSubscriptionRepositoryAdapter implements CustomerSubscriptionRepository {

    private final SpringDataCustomerSubscriptionRepository springDataRepository;
    private final SpringDataPricingPlanRepository pricingPlanRepository;
    private final CustomerSubscriptionMapper subscriptionMapper;
    private final PricingPlanMapper pricingPlanMapper;

    @Override
    public Optional<CustomerSubscription> findActiveSubscription(String customerId, LocalDate date) {
        return springDataRepository.findActiveSubscription(customerId, date)
                .map(po -> {
                    PricingPlan plan = pricingPlanRepository.findById(po.getPlanId())
                            .map(pricingPlanMapper::toDomain)
                            .orElse(null);
                    return subscriptionMapper.toDomain(po, plan);
                });
    }
}
