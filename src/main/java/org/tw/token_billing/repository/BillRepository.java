package org.tw.token_billing.repository;

import java.time.LocalDateTime;

import org.tw.token_billing.domain.Bill;

public interface BillRepository {
    Bill save(Bill bill);
    Integer sumIncludedTokensUsedForMonth(String customerId, LocalDateTime monthStart, LocalDateTime monthEnd);
}
