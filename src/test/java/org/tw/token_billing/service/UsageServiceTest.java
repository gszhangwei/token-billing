package org.tw.token_billing.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tw.token_billing.dto.UsageRequest;
import org.tw.token_billing.entity.*;
import org.tw.token_billing.repository.BillRepository;
import org.tw.token_billing.repository.CustomerSubscriptionRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UsageService.
 * These tests verify the billing calculation logic with mocked repositories.
 */
@ExtendWith(MockitoExtension.class)
class UsageServiceTest {

    @Mock
    private CustomerSubscriptionRepository subscriptionRepository;

    @Mock
    private BillRepository billRepository;

    private UsageService usageService;

    @BeforeEach
    void setUp() {
        usageService = new UsageService(subscriptionRepository, billRepository);
    }

    private CustomerSubscription createFullSubscription(String customerId) {
        // Create a complete subscription chain with all entities
        Instant now = Instant.now();
        
        Customer customer = new Customer(customerId, "Test Customer", now);
        PricingPlan plan = new PricingPlan("PLAN-STARTER", "Starter", 100000, new BigDecimal("0.0200"), now);
        
        CustomerSubscription subscription = new CustomerSubscription();
        subscription.setId(UUID.randomUUID());
        subscription.setCustomer(customer);
        subscription.setPricingPlan(plan);
        subscription.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        subscription.setCreatedAt(now);
        
        return subscription;
    }

    @Test
    void calculateBill_withinQuota_returnsZeroCharge() {
        // Given: Customer has 100,000 quota, no previous usage
        CustomerSubscription subscription = createFullSubscription("CUST-001");
        
        when(subscriptionRepository.findActiveSubscription(any(), any())).thenReturn(Optional.of(subscription));
        when(billRepository.sumTotalTokensByCustomerIdAndMonthStart(any(), any())).thenReturn(0L);

        // When: Submitting 30,000 tokens (within 100,000 quota)
        UsageRequest request = new UsageRequest("CUST-001", 15000, 15000);
        var response = usageService.calculateBill(request);

        // Then: All tokens from quota, zero charge
        assertEquals("CUST-001", response.getCustomerId());
        assertEquals(30000, response.getTotalTokens());
        assertEquals(30000, response.getTokensFromQuota());
        assertEquals(0, response.getOverageTokens());
        assertEquals(0, response.getTotalCharge().compareTo(BigDecimal.ZERO));
        assertEquals("USD", response.getCurrency());
    }

    @Test
    void calculateBill_exceedingQuota_returnsCorrectCharge() {
        // Given: Customer has 100,000 quota, 80,000 already used
        CustomerSubscription subscription = createFullSubscription("CUST-001");
        
        when(subscriptionRepository.findActiveSubscription(any(), any())).thenReturn(Optional.of(subscription));
        // Simulate 80,000 already used - leaves 20,000 quota available
        when(billRepository.sumTotalTokensByCustomerIdAndMonthStart(any(), any())).thenReturn(80000L);

        // When: Submitting 50,000 tokens
        UsageRequest request = new UsageRequest("CUST-001", 25000, 25000);
        var response = usageService.calculateBill(request);

        // Then: 20,000 from quota, 30,000 overage, $0.60 charge
        assertEquals(50000, response.getTotalTokens());
        assertEquals(20000, response.getTokensFromQuota());
        assertEquals(30000, response.getOverageTokens());
        assertEquals(0, new BigDecimal("0.60").compareTo(response.getTotalCharge()));
    }

    @Test
    void calculateBill_customerNotFound_throwsException() {
        when(subscriptionRepository.findActiveSubscription(any(), any())).thenReturn(Optional.empty());

        UsageRequest request = new UsageRequest("INVALID", 1000, 1000);
        assertThrows(UsageService.CustomerNotFoundException.class, () -> usageService.calculateBill(request));
    }

    @Test
    void calculateBill_responseContainsRequiredFields() {
        CustomerSubscription subscription = createFullSubscription("CUST-001");
        
        when(subscriptionRepository.findActiveSubscription(any(), any())).thenReturn(Optional.of(subscription));
        when(billRepository.sumTotalTokensByCustomerIdAndMonthStart(any(), any())).thenReturn(0L);

        UsageRequest request = new UsageRequest("CUST-001", 5000, 5000);
        var response = usageService.calculateBill(request);

        // Verify all required fields are present
        assertNotNull(response.getBillId());
        assertNotNull(response.getCustomerId());
        assertNotNull(response.getPromptTokens());
        assertNotNull(response.getCompletionTokens());
        assertNotNull(response.getTotalTokens());
        assertNotNull(response.getTokensFromQuota());
        assertNotNull(response.getOverageTokens());
        assertNotNull(response.getTotalCharge());
        assertNotNull(response.getCurrency());
        assertNotNull(response.getCalculatedAt());
    }
}