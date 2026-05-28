package org.tw.token_billing.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.tw.token_billing.entity.CustomerSubscription;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerSubscriptionRepository extends JpaRepository<CustomerSubscription, UUID> {

    @Query("""
            SELECT cs FROM CustomerSubscription cs
            WHERE cs.customerId = :customerId
              AND cs.effectiveFrom <= :asOfDate
              AND (cs.effectiveTo IS NULL OR cs.effectiveTo >= :asOfDate)
            ORDER BY cs.effectiveFrom DESC
            """)
    List<CustomerSubscription> findActiveByCustomerId(
            @Param("customerId") String customerId,
            @Param("asOfDate") LocalDate asOfDate,
            Pageable pageable);

    default Optional<CustomerSubscription> findActiveByCustomerId(String customerId, LocalDate asOfDate) {
        List<CustomerSubscription> results = findActiveByCustomerId(customerId, asOfDate, Pageable.ofSize(1));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }
}
