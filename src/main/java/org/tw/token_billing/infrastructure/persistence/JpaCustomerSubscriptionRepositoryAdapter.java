package org.tw.token_billing.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;
import org.tw.token_billing.domain.CustomerSubscription;
import org.tw.token_billing.domain.PricingPlan;
import org.tw.token_billing.infrastructure.persistence.entity.CustomerSubscriptionPO;
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
    public List<CustomerSubscription> findActiveSubscriptions(String customerId, LocalDate date) {
        List<CustomerSubscriptionPO> subscriptionPOs = springDataRepository.findActiveSubscriptions(customerId, date);

        return subscriptionPOs.stream()
                .map(po -> {
                    PricingPlan plan = pricingPlanRepository.findById(po.getPlanId())
                            .map(pricingPlanMapper::toDomain)
                            .orElse(null);
                    return subscriptionMapper.toDomain(po, plan);
                })
                .collect(Collectors.toList());
    }
}
