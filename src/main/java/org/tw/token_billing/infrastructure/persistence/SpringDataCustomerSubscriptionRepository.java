package org.tw.token_billing.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.tw.token_billing.infrastructure.persistence.entity.CustomerSubscriptionPO;

public interface SpringDataCustomerSubscriptionRepository extends JpaRepository<CustomerSubscriptionPO, UUID> {

    @Query("SELECT cs FROM CustomerSubscriptionPO cs WHERE cs.customerId = :customerId " +
           "AND cs.effectiveFrom <= :date AND (cs.effectiveTo IS NULL OR cs.effectiveTo >= :date) " +
           "ORDER BY cs.createdAt DESC")
    List<CustomerSubscriptionPO> findActiveSubscriptions(@Param("customerId") String customerId,
                                                          @Param("date") LocalDate date);
}
