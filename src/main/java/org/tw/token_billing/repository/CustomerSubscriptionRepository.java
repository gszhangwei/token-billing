package org.tw.token_billing.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.tw.token_billing.domain.CustomerSubscription;

public interface CustomerSubscriptionRepository {
    Optional<CustomerSubscription> findActiveSubscription(String customerId, LocalDate date);
}
