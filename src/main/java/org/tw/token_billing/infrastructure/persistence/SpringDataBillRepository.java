package org.tw.token_billing.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.tw.token_billing.infrastructure.persistence.entity.BillPO;

public interface SpringDataBillRepository extends JpaRepository<BillPO, UUID> {

    @Query("SELECT COALESCE(SUM(b.includedTokensUsed), 0) FROM BillPO b " +
           "WHERE b.customerId = :customerId " +
           "AND b.calculatedAt >= :monthStart AND b.calculatedAt < :monthEnd")
    Integer sumIncludedTokensUsedForMonth(@Param("customerId") String customerId,
                                          @Param("monthStart") LocalDateTime monthStart,
                                          @Param("monthEnd") LocalDateTime monthEnd);
}
