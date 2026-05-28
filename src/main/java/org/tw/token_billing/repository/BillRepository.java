package org.tw.token_billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.tw.token_billing.entity.Bill;

import java.time.Instant;
import java.util.UUID;

public interface BillRepository extends JpaRepository<Bill, UUID> {

    @Query("""
            SELECT COALESCE(SUM(b.totalTokens), 0) FROM Bill b
            WHERE b.customerId = :customerId
              AND b.calculatedAt >= :monthStart
              AND b.calculatedAt < :monthEnd
            """)
    Long sumTotalTokensByCustomerIdAndCalculatedAtBetween(
            @Param("customerId") String customerId,
            @Param("monthStart") Instant monthStart,
            @Param("monthEnd") Instant monthEnd);
}
