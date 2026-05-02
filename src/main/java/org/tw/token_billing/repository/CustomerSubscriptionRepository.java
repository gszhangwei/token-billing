package org.tw.token_billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.tw.token_billing.entity.CustomerSubscription;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface CustomerSubscriptionRepository extends JpaRepository<CustomerSubscription, UUID> {

    @Query("SELECT s FROM CustomerSubscription s " +
           "JOIN FETCH s.customer " +
           "JOIN FETCH s.pricingPlan " +
           "WHERE s.customer.id = :customerId " +
           "AND s.effectiveFrom <= :effectiveDate " +
           "AND (s.effectiveTo IS NULL OR s.effectiveTo >= :effectiveDate)")
    Optional<CustomerSubscription> findActiveSubscription(
        @Param("customerId") String customerId,
        @Param("effectiveDate") LocalDate effectiveDate
    );
}