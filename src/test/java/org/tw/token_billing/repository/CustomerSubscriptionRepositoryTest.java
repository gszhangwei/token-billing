package org.tw.token_billing.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.tw.token_billing.entity.Customer;
import org.tw.token_billing.entity.CustomerSubscription;
import org.tw.token_billing.entity.PricingPlan;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for CustomerSubscriptionRepository.findAllActiveSubscriptions
 * against a real PostgreSQL container.
 *
 * Covers issue #3 acceptance criteria:
 *   - effective_from = today (inclusive)
 *   - effective_to = today (inclusive)
 *   - effective_to = today - 1 (expired)
 *   - zero / multiple active rows for a customer (CUST-004 / CUST-005 fixtures)
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CustomerSubscriptionRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private CustomerSubscriptionRepository subscriptionRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Customer findCustomer(String id) {
        Customer customer = entityManager.find(Customer.class, id);
        assertNotNull(customer, "Flyway seed should provide " + id);
        return customer;
    }

    private PricingPlan findPlan(String id) {
        PricingPlan plan = entityManager.find(PricingPlan.class, id);
        assertNotNull(plan, "Flyway seed should provide " + id);
        return plan;
    }

    private Customer freshCustomer(String id) {
        Customer customer = new Customer(id, "Test " + id, Instant.now());
        entityManager.persist(customer);
        return customer;
    }

    private CustomerSubscription persistSubscription(Customer customer, LocalDate from, LocalDate to) {
        CustomerSubscription sub = new CustomerSubscription();
        sub.setId(UUID.randomUUID());
        sub.setCustomer(customer);
        sub.setPricingPlan(findPlan("PLAN-STARTER"));
        sub.setEffectiveFrom(from);
        sub.setEffectiveTo(to);
        sub.setCreatedAt(Instant.now());
        entityManager.persist(sub);
        return sub;
    }

    // -------- AC: effective_from = today (inclusive) --------

    @Test
    void findAll_effectiveFromToday_isActive() {
        LocalDate today = LocalDate.of(2026, 6, 15);
        Customer customer = freshCustomer("CUST-BOUND-FROM-TODAY");
        persistSubscription(customer, today, null);
        entityManager.flush();

        List<CustomerSubscription> result = subscriptionRepository
            .findAllActiveSubscriptions(customer.getId(), today);

        assertEquals(1, result.size(),
            "Subscription whose effective_from equals today is active (inclusive)");
    }

    // -------- AC: effective_to = today (inclusive) --------

    @Test
    void findAll_effectiveToToday_isActive() {
        LocalDate today = LocalDate.of(2026, 6, 15);
        Customer customer = freshCustomer("CUST-BOUND-TO-TODAY");
        persistSubscription(customer, today.minusYears(1), today);
        entityManager.flush();

        List<CustomerSubscription> result = subscriptionRepository
            .findAllActiveSubscriptions(customer.getId(), today);

        assertEquals(1, result.size(),
            "Subscription whose effective_to equals today is active on its last day (inclusive)");
    }

    // -------- AC: effective_to = today - 1 (expired) --------

    @Test
    void findAll_effectiveToYesterday_isExpired() {
        LocalDate today = LocalDate.of(2026, 6, 15);
        Customer customer = freshCustomer("CUST-BOUND-EXPIRED");
        persistSubscription(customer, today.minusYears(1), today.minusDays(1));
        entityManager.flush();

        List<CustomerSubscription> result = subscriptionRepository
            .findAllActiveSubscriptions(customer.getId(), today);

        assertTrue(result.isEmpty(),
            "Subscription whose effective_to is yesterday must not be returned today");
    }

    // -------- AC: effective_from = tomorrow (not yet active) --------

    @Test
    void findAll_effectiveFromTomorrow_isNotYetActive() {
        LocalDate today = LocalDate.of(2026, 6, 15);
        Customer customer = freshCustomer("CUST-BOUND-FUTURE");
        persistSubscription(customer, today.plusDays(1), null);
        entityManager.flush();

        List<CustomerSubscription> result = subscriptionRepository
            .findAllActiveSubscriptions(customer.getId(), today);

        assertTrue(result.isEmpty(),
            "Subscription whose effective_from is tomorrow must not be active today");
    }

    // -------- AC6: zero active subscriptions on seeded fixture --------

    @Test
    void findAll_seededCustomerWithExpiredSubscription_returnsEmpty() {
        // CUST-004 has only an expired (2020-01-01..2020-01-02) row in seed
        List<CustomerSubscription> result = subscriptionRepository
            .findAllActiveSubscriptions("CUST-004", LocalDate.of(2026, 6, 15));

        assertTrue(result.isEmpty(),
            "CUST-004 should have zero active subscriptions per seed fixture");
    }

    // -------- Multiple active subscriptions on seeded fixture --------

    @Test
    void findAll_seededCustomerWithMultipleActive_returnsAll() {
        // CUST-005 has two PLAN-STARTER + PLAN-PRO rows both with NULL effective_to
        List<CustomerSubscription> result = subscriptionRepository
            .findAllActiveSubscriptions("CUST-005", LocalDate.of(2026, 6, 15));

        assertEquals(2, result.size(),
            "CUST-005 should have two active subscriptions per seed fixture");
    }
}
