package org.tw.token_billing.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tw.token_billing.dto.BillResponse;
import org.tw.token_billing.dto.QuotaStatusResponse;
import org.tw.token_billing.dto.UsageRequest;
import org.tw.token_billing.entity.CustomerSubscription;
import org.tw.token_billing.entity.PricingPlan;
import org.tw.token_billing.exception.CustomerNotFoundException;
import org.tw.token_billing.repository.BillRepository;
import org.tw.token_billing.repository.CustomerRepository;
import org.tw.token_billing.repository.CustomerSubscriptionRepository;
import org.tw.token_billing.repository.PricingPlanRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerSubscriptionRepository customerSubscriptionRepository;

    @Mock
    private PricingPlanRepository pricingPlanRepository;

    @Mock
    private BillRepository billRepository;

    @InjectMocks
    private BillingServiceImpl billingService;

    @Test
    void should_throw_when_customer_not_found() {
        when(customerRepository.existsById("UNKNOWN")).thenReturn(false);

        assertThatThrownBy(() -> billingService.submitUsage(new UsageRequest("UNKNOWN", 100, 50)))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void should_bill_within_quota() {
        stubActiveStarterPlan();
        when(billRepository.sumTotalTokensByCustomerIdAndCalculatedAtBetween(eq("CUST-001"), any(), any()))
                .thenReturn(60_000L);
        when(billRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BillResponse response = billingService.submitUsage(new UsageRequest("CUST-001", 20_000, 10_000));

        assertThat(response.totalTokens()).isEqualTo(30_000);
        assertThat(response.includedTokensUsed()).isEqualTo(30_000);
        assertThat(response.overageTokens()).isZero();
        assertThat(response.totalCharge()).isEqualByComparingTo("0.00");
    }

    @Test
    void should_bill_with_overage_charge() {
        stubActiveStarterPlan();
        when(billRepository.sumTotalTokensByCustomerIdAndCalculatedAtBetween(eq("CUST-001"), any(), any()))
                .thenReturn(80_000L);
        when(billRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BillResponse response = billingService.submitUsage(new UsageRequest("CUST-001", 30_000, 20_000));

        assertThat(response.totalTokens()).isEqualTo(50_000);
        assertThat(response.includedTokensUsed()).isEqualTo(20_000);
        assertThat(response.overageTokens()).isEqualTo(30_000);
        assertThat(response.totalCharge()).isEqualByComparingTo("0.60");
    }

    @Test
    void should_throw_when_customer_not_found_on_quota_lookup() {
        when(customerRepository.existsById("UNKNOWN")).thenReturn(false);

        assertThatThrownBy(() -> billingService.getQuotaStatus("UNKNOWN"))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void should_return_quota_status_within_quota() {
        stubActiveStarterPlan();
        when(billRepository.sumTotalTokensByCustomerIdAndCalculatedAtBetween(eq("CUST-001"), any(), any()))
                .thenReturn(60_000L);

        QuotaStatusResponse response = billingService.getQuotaStatus("CUST-001");

        assertThat(response.customerId()).isEqualTo("CUST-001");
        assertThat(response.monthlyQuota()).isEqualTo(100_000);
        assertThat(response.tokensUsedThisMonth()).isEqualTo(60_000L);
        assertThat(response.remainingQuota()).isEqualTo(40_000);
        assertThat(response.overageRatePer1k()).isEqualByComparingTo("0.02");
    }

    @Test
    void should_floor_remaining_quota_at_zero() {
        stubActiveStarterPlan();
        when(billRepository.sumTotalTokensByCustomerIdAndCalculatedAtBetween(eq("CUST-001"), any(), any()))
                .thenReturn(120_000L);

        QuotaStatusResponse response = billingService.getQuotaStatus("CUST-001");

        assertThat(response.tokensUsedThisMonth()).isEqualTo(120_000L);
        assertThat(response.remainingQuota()).isZero();
    }

    @Test
    void should_return_full_quota_when_no_usage_this_month() {
        stubActiveStarterPlan();
        when(billRepository.sumTotalTokensByCustomerIdAndCalculatedAtBetween(eq("CUST-001"), any(), any()))
                .thenReturn(0L);

        QuotaStatusResponse response = billingService.getQuotaStatus("CUST-001");

        assertThat(response.tokensUsedThisMonth()).isZero();
        assertThat(response.remainingQuota()).isEqualTo(100_000);
    }

    private void stubActiveStarterPlan() {
        when(customerRepository.existsById("CUST-001")).thenReturn(true);

        CustomerSubscription subscription = new CustomerSubscription();
        subscription.setId(UUID.randomUUID());
        subscription.setCustomerId("CUST-001");
        subscription.setPlanId("PLAN-STARTER");
        subscription.setEffectiveFrom(LocalDate.of(2026, 1, 1));

        PricingPlan plan = new PricingPlan();
        plan.setId("PLAN-STARTER");
        plan.setMonthlyQuota(100_000);
        plan.setOverageRatePer1k(new BigDecimal("0.0200"));

        when(customerSubscriptionRepository.findActiveByCustomerId(eq("CUST-001"), any(LocalDate.class)))
                .thenReturn(Optional.of(subscription));
        when(pricingPlanRepository.findById("PLAN-STARTER")).thenReturn(Optional.of(plan));
    }
}
