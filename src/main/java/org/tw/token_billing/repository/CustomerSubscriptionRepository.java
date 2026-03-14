package org.tw.token_billing.repository;

import java.time.LocalDate;
import java.util.List;

import org.tw.token_billing.domain.CustomerSubscription;

public interface CustomerSubscriptionRepository {
    List<CustomerSubscription> findActiveSubscriptions(String customerId, LocalDate date);
}
