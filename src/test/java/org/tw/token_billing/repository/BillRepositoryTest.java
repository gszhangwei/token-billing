package org.tw.token_billing.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.tw.token_billing.entity.Bill;
import org.tw.token_billing.entity.Customer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration test for BillRepository against H2 (PostgreSQL mode) with Flyway migrations.
 * Verifies the SUM query returns Long, COALESCE-to-zero on empty, and Instant-based
 * month boundary filtering — all behaviors from review #1.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BillRepositoryTest {

    private static final String CUSTOMER_A = "CUST-001";
    private static final String CUSTOMER_B = "CUST-002";
    private static final Instant MAY_1ST = Instant.parse("2026-05-01T00:00:00Z");

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Customer findCustomer(String id) {
        Customer customer = entityManager.find(Customer.class, id);
        assertNotNull(customer, "Flyway seed should provide " + id);
        return customer;
    }

    private Bill bill(Customer customer, int totalTokens, Instant calculatedAt) {
        return new Bill(
            UUID.randomUUID(),
            customer,
            totalTokens / 2,
            totalTokens - totalTokens / 2,
            totalTokens,
            totalTokens,
            0,
            BigDecimal.ZERO,
            calculatedAt
        );
    }

    @Test
    void sumTotalTokens_emptyTable_returnsZeroLong() {
        Long sum = billRepository.sumTotalTokensByCustomerIdAndMonthStart(CUSTOMER_A, MAY_1ST);

        assertNotNull(sum, "COALESCE should never return null");
        assertEquals(0L, sum);
    }

    @Test
    void sumTotalTokens_multipleBills_returnsTotal() {
        Customer customer = findCustomer(CUSTOMER_A);
        Instant inMay = Instant.parse("2026-05-15T12:00:00Z");

        entityManager.persist(bill(customer, 1000, inMay));
        entityManager.persist(bill(customer, 2500, inMay));
        entityManager.flush();

        Long sum = billRepository.sumTotalTokensByCustomerIdAndMonthStart(CUSTOMER_A, MAY_1ST);

        assertEquals(3500L, sum);
    }

    @Test
    void sumTotalTokens_filtersOutPriorMonth() {
        Customer customer = findCustomer(CUSTOMER_A);

        // 上月最後一秒 — 應排除
        entityManager.persist(bill(customer, 9999, Instant.parse("2026-04-30T23:59:59Z")));
        // 本月第一秒 — 應計入
        entityManager.persist(bill(customer, 1000, Instant.parse("2026-05-01T00:00:00Z")));
        entityManager.persist(bill(customer, 2000, Instant.parse("2026-05-15T00:00:00Z")));
        entityManager.flush();

        Long sum = billRepository.sumTotalTokensByCustomerIdAndMonthStart(CUSTOMER_A, MAY_1ST);

        assertEquals(3000L, sum, "Should exclude bills before monthStart");
    }

    @Test
    void sumTotalTokens_filtersOtherCustomers() {
        Customer a = findCustomer(CUSTOMER_A);
        Customer b = findCustomer(CUSTOMER_B);
        Instant inMay = Instant.parse("2026-05-15T12:00:00Z");

        entityManager.persist(bill(a, 1500, inMay));
        entityManager.persist(bill(b, 9999, inMay));
        entityManager.flush();

        Long sum = billRepository.sumTotalTokensByCustomerIdAndMonthStart(CUSTOMER_A, MAY_1ST);

        assertEquals(1500L, sum, "Should only count bills for the specified customer");
    }
}
