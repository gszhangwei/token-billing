package org.tw.token_billing.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tw.token_billing.dto.UsageRequest;
import org.tw.token_billing.entity.*;
import org.tw.token_billing.repository.BillRepository;
import org.tw.token_billing.repository.CustomerSubscriptionRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsageServiceTest {

    private static final String CUSTOMER_ID = "CUST-001";
    private static final int DEFAULT_QUOTA = 100_000;
    private static final BigDecimal DEFAULT_RATE = new BigDecimal("0.0200");

    @Mock
    private CustomerSubscriptionRepository subscriptionRepository;

    @Mock
    private BillRepository billRepository;

    private UsageService usageService;

    @BeforeEach
    void setUp() {
        usageService = new UsageService(subscriptionRepository, billRepository);
    }

    private CustomerSubscription createSubscription(String customerId, int quota, BigDecimal ratePer1k) {
        Instant now = Instant.now();
        Customer customer = new Customer(customerId, "Test Customer", now);
        PricingPlan plan = new PricingPlan("PLAN-X", "Plan X", quota, ratePer1k, now);

        CustomerSubscription subscription = new CustomerSubscription();
        subscription.setId(UUID.randomUUID());
        subscription.setCustomer(customer);
        subscription.setPricingPlan(plan);
        subscription.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        subscription.setCreatedAt(now);
        return subscription;
    }

    private CustomerSubscription createDefaultSubscription() {
        return createSubscription(CUSTOMER_ID, DEFAULT_QUOTA, DEFAULT_RATE);
    }

    private void mockUsage(CustomerSubscription subscription, long currentMonthUsage) {
        when(subscriptionRepository.findActiveSubscription(any(), any()))
            .thenReturn(Optional.of(subscription));
        when(billRepository.sumTotalTokensByCustomerIdAndMonthStart(any(), any()))
            .thenReturn(currentMonthUsage);
    }

    // -------- Quota / overage logic --------

    @Test
    void calculateBill_withinQuota_returnsZeroCharge() {
        mockUsage(createDefaultSubscription(), 0L);

        var response = usageService.calculateBill(new UsageRequest(CUSTOMER_ID, 15000, 15000));

        assertEquals(CUSTOMER_ID, response.getCustomerId());
        assertEquals(30000, response.getTotalTokens());
        assertEquals(30000, response.getTokensFromQuota());
        assertEquals(0, response.getOverageTokens());
        assertEquals(0, response.getTotalCharge().compareTo(BigDecimal.ZERO));
        assertEquals("USD", response.getCurrency());
    }

    @Test
    void calculateBill_exceedingQuota_returnsCorrectCharge() {
        // 80,000 已用,提交 50,000 → 20,000 quota + 30,000 overage × $0.02/1k = $0.60
        mockUsage(createDefaultSubscription(), 80_000L);

        var response = usageService.calculateBill(new UsageRequest(CUSTOMER_ID, 25000, 25000));

        assertEquals(50000, response.getTotalTokens());
        assertEquals(20000, response.getTokensFromQuota());
        assertEquals(30000, response.getOverageTokens());
        assertEquals(0, new BigDecimal("0.60").compareTo(response.getTotalCharge()));
    }

    @Test
    void calculateBill_atExactQuotaBoundary_lastTokenStillFromQuota() {
        // 99,000 已用,提交 1,000 → 剛好用完配額,無 overage
        mockUsage(createDefaultSubscription(), 99_000L);

        var response = usageService.calculateBill(new UsageRequest(CUSTOMER_ID, 500, 500));

        assertEquals(1000, response.getTokensFromQuota());
        assertEquals(0, response.getOverageTokens());
        assertEquals(0, response.getTotalCharge().compareTo(BigDecimal.ZERO));
    }

    @Test
    void calculateBill_oneTokenOverQuota_splitsBetweenQuotaAndOverage() {
        // 99,999 已用,提交 2 → 1 from quota, 1 overage
        mockUsage(createDefaultSubscription(), 99_999L);

        var response = usageService.calculateBill(new UsageRequest(CUSTOMER_ID, 1, 1));

        assertEquals(1, response.getTokensFromQuota());
        assertEquals(1, response.getOverageTokens());
        // 1 token / 1000 * 0.02 = 0.00002 → setScale(2) = 0.00
        assertEquals(0, new BigDecimal("0.00").compareTo(response.getTotalCharge()));
    }

    @Test
    void calculateBill_currentUsageExceedsQuota_allOverage() {
        // 已用 110k 超過 100k quota → availableQuota=0, 提交全部 overage
        mockUsage(createDefaultSubscription(), 110_000L);

        var response = usageService.calculateBill(new UsageRequest(CUSTOMER_ID, 2500, 2500));

        assertEquals(0, response.getTokensFromQuota());
        assertEquals(5000, response.getOverageTokens());
        // 5000 / 1000 * 0.02 = 0.10
        assertEquals(0, new BigDecimal("0.10").compareTo(response.getTotalCharge()));
    }

    // -------- HALF_EVEN (banker's rounding) --------

    @Test
    void calculateBill_halfEvenRoundsToEvenWhenPriorDigitEven() {
        // rate=0.0050, overage=5000 → 5 × 0.005 = 0.0250 → setScale(2, HALF_EVEN): 0.02 (前位 2 偶)
        var subscription = createSubscription(CUSTOMER_ID, 0, new BigDecimal("0.0050"));
        mockUsage(subscription, 0L);

        var response = usageService.calculateBill(new UsageRequest(CUSTOMER_ID, 2500, 2500));

        assertEquals(5000, response.getOverageTokens());
        assertEquals(0, new BigDecimal("0.02").compareTo(response.getTotalCharge()),
            "HALF_EVEN should round 0.025 down when preceding digit is even (2)");
    }

    @Test
    void calculateBill_halfEvenRoundsUpWhenPriorDigitOdd() {
        // rate=0.0050, overage=7000 → 7 × 0.005 = 0.0350 → setScale(2, HALF_EVEN): 0.04 (前位 3 奇)
        var subscription = createSubscription(CUSTOMER_ID, 0, new BigDecimal("0.0050"));
        mockUsage(subscription, 0L);

        var response = usageService.calculateBill(new UsageRequest(CUSTOMER_ID, 3500, 3500));

        assertEquals(7000, response.getOverageTokens());
        assertEquals(0, new BigDecimal("0.04").compareTo(response.getTotalCharge()),
            "HALF_EVEN should round 0.035 up when preceding digit is odd (3)");
    }

    // -------- Errors --------

    @Test
    void calculateBill_customerNotFound_throwsException() {
        when(subscriptionRepository.findActiveSubscription(any(), any())).thenReturn(Optional.empty());

        var ex = assertThrows(UsageService.CustomerNotFoundException.class,
            () -> usageService.calculateBill(new UsageRequest("INVALID", 1000, 1000)));
        assertTrue(ex.getMessage().contains("INVALID"),
            "Exception message should include customerId");
        assertTrue(ex.getMessage().toLowerCase().contains("subscription"),
            "Exception message should mention subscription, not just 'customer not found'");
    }

    // -------- Persistence / interaction --------

    @Test
    void calculateBill_queriesUsageWithUtcMonthStart() {
        mockUsage(createDefaultSubscription(), 0L);

        usageService.calculateBill(new UsageRequest(CUSTOMER_ID, 100, 100));

        ArgumentCaptor<Instant> monthStartCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(billRepository).sumTotalTokensByCustomerIdAndMonthStart(
            eq(CUSTOMER_ID), monthStartCaptor.capture());

        Instant expected = LocalDate.now(ZoneOffset.UTC)
            .with(TemporalAdjusters.firstDayOfMonth())
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant();
        assertEquals(expected, monthStartCaptor.getValue(),
            "monthStart should be the first of the current UTC month at 00:00 UTC");
    }

    @Test
    void calculateBill_persistsBillWithCorrectFields() {
        var subscription = createDefaultSubscription();
        mockUsage(subscription, 80_000L);

        Instant before = Instant.now();
        var response = usageService.calculateBill(new UsageRequest(CUSTOMER_ID, 25000, 25000));
        Instant after = Instant.now();

        ArgumentCaptor<Bill> billCaptor = ArgumentCaptor.forClass(Bill.class);
        verify(billRepository).save(billCaptor.capture());
        Bill saved = billCaptor.getValue();

        assertNotNull(saved.getId());
        assertEquals(CUSTOMER_ID, saved.getCustomer().getId());
        assertEquals(25000, saved.getPromptTokens());
        assertEquals(25000, saved.getCompletionTokens());
        assertEquals(50000, saved.getTotalTokens());
        assertEquals(20000, saved.getIncludedTokensUsed());
        assertEquals(30000, saved.getOverageTokens());
        assertEquals(0, new BigDecimal("0.60").compareTo(saved.getTotalCharge()));
        assertFalse(saved.getCalculatedAt().isBefore(before));
        assertFalse(saved.getCalculatedAt().isAfter(after));

        // Response 應反映持久化的 bill
        assertEquals(saved.getId(), response.getBillId());
        assertEquals(saved.getCalculatedAt(), response.getCalculatedAt());
    }

    @Test
    void calculateBill_responseContainsRequiredFields() {
        mockUsage(createDefaultSubscription(), 0L);

        var response = usageService.calculateBill(new UsageRequest(CUSTOMER_ID, 5000, 5000));

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

    // -------- Issue #1 acceptance: AC3 / AC4 worked examples --------

    @Test
    void ac3_priorUsage60kPlus30kSubmit_returnsZeroCharge() {
        // AC3: CUST-001 STARTER, prior 60,000 + submit 30,000 → totalCharge=0.00
        mockUsage(createDefaultSubscription(), 60_000L);

        var response = usageService.calculateBill(new UsageRequest(CUSTOMER_ID, 15000, 15000));

        assertEquals(30000, response.getTokensFromQuota());
        assertEquals(0, response.getOverageTokens());
        assertEquals(0, new BigDecimal("0.00").compareTo(response.getTotalCharge()));
    }

    // SRS §2.4 worked-example tests. The (12500, 0.0150) case differs from the
    // SRS table entry: SRS lists 0.18 but the correct BigDecimal HALF_EVEN value
    // is 0.19 because 0.18750 is closer to 0.19 (distance 0.0025) than to 0.18
    // (distance 0.0075) — i.e. not a true tie, so HALF_EVEN behaves as HALF_UP.
    // SRS §2.4 row 5 is a documentation errata to be corrected.

    @Test
    void srsWorkedExample_overage12345_at_rate_0_0150_yields_0_19() {
        // 12345 / 1000 × 0.0150 = 0.185175 → setScale(2, HALF_EVEN) = 0.19
        var subscription = createSubscription(CUSTOMER_ID, 0, new BigDecimal("0.0150"));
        mockUsage(subscription, 0L);

        var response = usageService.calculateBill(new UsageRequest(CUSTOMER_ID, 6172, 6173));

        assertEquals(12345, response.getOverageTokens());
        assertEquals(0, new BigDecimal("0.19").compareTo(response.getTotalCharge()));
    }

    @Test
    void srsWorkedExample_overage999_at_rate_0_0200_yields_0_02() {
        // 999 / 1000 × 0.0200 = 0.019980 → setScale(2, HALF_EVEN) = 0.02
        var subscription = createSubscription(CUSTOMER_ID, 0, new BigDecimal("0.0200"));
        mockUsage(subscription, 0L);

        var response = usageService.calculateBill(new UsageRequest(CUSTOMER_ID, 499, 500));

        assertEquals(999, response.getOverageTokens());
        assertEquals(0, new BigDecimal("0.02").compareTo(response.getTotalCharge()));
    }

    @Test
    void srsWorkedExample_overage5_at_rate_0_0150_yields_0_00() {
        // 5 / 1000 × 0.0150 = 0.000075 → setScale(2, HALF_EVEN) = 0.00
        var subscription = createSubscription(CUSTOMER_ID, 0, new BigDecimal("0.0150"));
        mockUsage(subscription, 0L);

        var response = usageService.calculateBill(new UsageRequest(CUSTOMER_ID, 2, 3));

        assertEquals(5, response.getOverageTokens());
        assertEquals(0, new BigDecimal("0.00").compareTo(response.getTotalCharge()));
    }

    @Test
    void srsWorkedExample_overage12500_at_rate_0_0150_yields_0_19() {
        // 12500 / 1000 × 0.0150 = 0.18750 → setScale(2, HALF_EVEN) = 0.19
        // (SRS §2.4 lists 0.18 — that entry is a documentation errata)
        var subscription = createSubscription(CUSTOMER_ID, 0, new BigDecimal("0.0150"));
        mockUsage(subscription, 0L);

        var response = usageService.calculateBill(new UsageRequest(CUSTOMER_ID, 6250, 6250));

        assertEquals(12500, response.getOverageTokens());
        assertEquals(0, new BigDecimal("0.19").compareTo(response.getTotalCharge()));
    }
}
