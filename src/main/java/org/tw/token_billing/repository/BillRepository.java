package org.tw.token_billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.tw.token_billing.entity.Bill;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BillRepository extends JpaRepository<Bill, UUID> {

    @Query("SELECT COALESCE(SUM(b.totalTokens), 0) FROM Bill b " +
           "WHERE b.customer.id = :customerId " +
           "AND b.calculatedAt >= :monthStart")
    Long sumTotalTokensByCustomerIdAndMonthStart(
        @Param("customerId") String customerId,
        @Param("monthStart") Instant monthStart
    );

    @Query("SELECT b FROM Bill b " +
           "WHERE b.customer.id = :customerId " +
           "AND b.idempotencyKey = :key " +
           "ORDER BY b.createdAt DESC")
    List<Bill> findActiveIdempotentBills(
        @Param("customerId") String customerId,
        @Param("key") String idempotencyKey);
}