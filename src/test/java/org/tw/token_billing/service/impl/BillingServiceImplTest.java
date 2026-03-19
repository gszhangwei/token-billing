package org.tw.token_billing.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tw.token_billing.domain.Bill;
import org.tw.token_billing.domain.Customer;
import org.tw.token_billing.domain.CustomerSubscription;
import org.tw.token_billing.domain.ModelPricing;
import org.tw.token_billing.domain.PlanType;
import org.tw.token_billing.domain.PricingPlan;
import org.tw.token_billing.dto.UsageRequest;
import org.tw.token_billing.exception.CustomerNotFoundException;
import org.tw.token_billing.exception.ModelPricingNotFoundException;
import org.tw.token_billing.exception.NoActiveSubscriptionException;
import org.tw.token_billing.repository.BillRepository;
import org.tw.token_billing.repository.CustomerRepository;
import org.tw.token_billing.repository.CustomerSubscriptionRepository;
import org.tw.token_billing.repository.ModelPricingRepository;
import org.tw.token_billing.service.strategy.BillingStrategyFactory;
import org.tw.token_billing.service.strategy.PremiumBillingStrategy;
import org.tw.token_billing.service.strategy.StandardBillingStrategy;

@ExtendWith(MockitoExtension.class)
class BillingServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerSubscriptionRepository customerSubscriptionRepository;

    @Mock
    private BillRepository billRepository;

    @Mock
    private ModelPricingRepository modelPricingRepository;

    @Mock
    private BillingStrategyFactory billingStrategyFactory;

    @InjectMocks
    private BillingServiceImpl billingService;

    private Customer customer;
    private PricingPlan pricingPlan;
    private CustomerSubscription subscription;
    private ModelPricing modelPricing;
    private static final String MODEL_ID = "fast-model";

    @BeforeEach
    void setUp() {
        customer = Customer.builder()
                .id("CUST-001")
                .name("Test Customer")
                .createdAt(LocalDateTime.now())
                .build();

        pricingPlan = PricingPlan.builder()
                .id("PLAN-STARTER")
                .name("Starter Plan")
                .planType(PlanType.STANDARD)
                .monthlyQuota(100000)
                .overageRatePer1k(new BigDecimal("0.02"))
                .createdAt(LocalDateTime.now())
                .build();

        subscription = CustomerSubscription.builder()
                .id(UUID.randomUUID())
                .customerId("CUST-001")
                .plan(pricingPlan)
                .effectiveFrom(LocalDate.of(2026, 1, 1))
                .effectiveTo(null)
                .createdAt(LocalDateTime.now())
                .build();

        modelPricing = ModelPricing.builder()
                .id(UUID.randomUUID())
                .planId("PLAN-STARTER")
                .modelId(MODEL_ID)
                .overageRatePer1k(new BigDecimal("0.02"))
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Should return bill with zero charge when usage is within quota")
    void should_return_bill_with_zero_charge_when_calculate_bill_given_usage_within_quota() {
        UsageRequest request = UsageRequest.builder()
                .customerId("CUST-001")
                .modelId(MODEL_ID)
                .promptTokens(1000)
                .completionTokens(500)
                .build();

        when(customerRepository.findById("CUST-001")).thenReturn(Optional.of(customer));
        when(customerSubscriptionRepository.findActiveSubscription(eq("CUST-001"), any(LocalDate.class)))
                .thenReturn(Optional.of(subscription));
        when(modelPricingRepository.findByPlanIdAndModelId("PLAN-STARTER", MODEL_ID))
                .thenReturn(Optional.of(modelPricing));
        when(billingStrategyFactory.getStrategy(PlanType.STANDARD)).thenReturn(new StandardBillingStrategy());
        when(billRepository.sumIncludedTokensUsedForMonth(eq("CUST-001"), any(), any())).thenReturn(0);
        when(billRepository.save(any(Bill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Bill result = billingService.calculateBill(request);

        assertThat(result.getTotalTokens()).isEqualTo(1500);
        assertThat(result.getIncludedTokensUsed()).isEqualTo(1500);
        assertThat(result.getOverageTokens()).isEqualTo(0);
        assertThat(result.getTotalCharge()).isEqualByComparingTo(BigDecimal.ZERO);

        verify(customerRepository).findById("CUST-001");
        verify(customerSubscriptionRepository).findActiveSubscription(eq("CUST-001"), any(LocalDate.class));
        verify(billRepository).sumIncludedTokensUsedForMonth(eq("CUST-001"), any(), any());
        verify(billRepository).save(any(Bill.class));
    }

    @Test
    @DisplayName("Should return bill with overage charge when usage exceeds quota")
    void should_return_bill_with_overage_charge_when_calculate_bill_given_usage_exceeds_quota() {
        PricingPlan smallQuotaPlan = PricingPlan.builder()
                .id("PLAN-FREE")
                .name("Free Plan")
                .planType(PlanType.STANDARD)
                .monthlyQuota(10000)
                .overageRatePer1k(new BigDecimal("0.02"))
                .createdAt(LocalDateTime.now())
                .build();

        CustomerSubscription smallQuotaSubscription = CustomerSubscription.builder()
                .id(UUID.randomUUID())
                .customerId("CUST-001")
                .plan(smallQuotaPlan)
                .effectiveFrom(LocalDate.of(2026, 1, 1))
                .effectiveTo(null)
                .createdAt(LocalDateTime.now())
                .build();

        ModelPricing smallPlanModelPricing = ModelPricing.builder()
                .id(UUID.randomUUID())
                .planId("PLAN-FREE")
                .modelId(MODEL_ID)
                .overageRatePer1k(new BigDecimal("0.02"))
                .createdAt(LocalDateTime.now())
                .build();

        UsageRequest request = UsageRequest.builder()
                .customerId("CUST-001")
                .modelId(MODEL_ID)
                .promptTokens(8000)
                .completionTokens(5000)
                .build();

        when(customerRepository.findById("CUST-001")).thenReturn(Optional.of(customer));
        when(customerSubscriptionRepository.findActiveSubscription(eq("CUST-001"), any(LocalDate.class)))
                .thenReturn(Optional.of(smallQuotaSubscription));
        when(modelPricingRepository.findByPlanIdAndModelId("PLAN-FREE", MODEL_ID))
                .thenReturn(Optional.of(smallPlanModelPricing));
        when(billingStrategyFactory.getStrategy(PlanType.STANDARD)).thenReturn(new StandardBillingStrategy());
        when(billRepository.sumIncludedTokensUsedForMonth(eq("CUST-001"), any(), any())).thenReturn(0);
        when(billRepository.save(any(Bill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Bill result = billingService.calculateBill(request);

        assertThat(result.getTotalTokens()).isEqualTo(13000);
        assertThat(result.getIncludedTokensUsed()).isEqualTo(10000);
        assertThat(result.getOverageTokens()).isEqualTo(3000);
        assertThat(result.getTotalCharge()).isEqualByComparingTo(new BigDecimal("0.06"));
    }

    @Test
    @DisplayName("Should return bill with full overage when quota is exhausted")
    void should_return_bill_with_full_overage_when_calculate_bill_given_zero_remaining_quota() {
        UsageRequest request = UsageRequest.builder()
                .customerId("CUST-001")
                .modelId(MODEL_ID)
                .promptTokens(1000)
                .completionTokens(500)
                .build();

        when(customerRepository.findById("CUST-001")).thenReturn(Optional.of(customer));
        when(customerSubscriptionRepository.findActiveSubscription(eq("CUST-001"), any(LocalDate.class)))
                .thenReturn(Optional.of(subscription));
        when(modelPricingRepository.findByPlanIdAndModelId("PLAN-STARTER", MODEL_ID))
                .thenReturn(Optional.of(modelPricing));
        when(billingStrategyFactory.getStrategy(PlanType.STANDARD)).thenReturn(new StandardBillingStrategy());
        when(billRepository.sumIncludedTokensUsedForMonth(eq("CUST-001"), any(), any())).thenReturn(100000);
        when(billRepository.save(any(Bill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Bill result = billingService.calculateBill(request);

        assertThat(result.getIncludedTokensUsed()).isEqualTo(0);
        assertThat(result.getOverageTokens()).isEqualTo(1500);
        assertThat(result.getTotalCharge()).isEqualByComparingTo(new BigDecimal("0.03"));
    }

    @Test
    @DisplayName("Should return bill with zero tokens when zero usage submitted")
    void should_return_bill_with_zero_tokens_when_calculate_bill_given_zero_usage() {
        UsageRequest request = UsageRequest.builder()
                .customerId("CUST-001")
                .modelId(MODEL_ID)
                .promptTokens(0)
                .completionTokens(0)
                .build();

        when(customerRepository.findById("CUST-001")).thenReturn(Optional.of(customer));
        when(customerSubscriptionRepository.findActiveSubscription(eq("CUST-001"), any(LocalDate.class)))
                .thenReturn(Optional.of(subscription));
        when(modelPricingRepository.findByPlanIdAndModelId("PLAN-STARTER", MODEL_ID))
                .thenReturn(Optional.of(modelPricing));
        when(billingStrategyFactory.getStrategy(PlanType.STANDARD)).thenReturn(new StandardBillingStrategy());
        when(billRepository.sumIncludedTokensUsedForMonth(eq("CUST-001"), any(), any())).thenReturn(0);
        when(billRepository.save(any(Bill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Bill result = billingService.calculateBill(request);

        assertThat(result.getTotalTokens()).isEqualTo(0);
        assertThat(result.getIncludedTokensUsed()).isEqualTo(0);
        assertThat(result.getOverageTokens()).isEqualTo(0);
        assertThat(result.getTotalCharge()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should throw CustomerNotFoundException when customer does not exist")
    void should_throw_customer_not_found_exception_when_calculate_bill_given_invalid_customer_id() {
        UsageRequest request = UsageRequest.builder()
                .customerId("INVALID-CUSTOMER")
                .modelId(MODEL_ID)
                .promptTokens(1000)
                .completionTokens(500)
                .build();

        when(customerRepository.findById("INVALID-CUSTOMER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> billingService.calculateBill(request))
                .isInstanceOf(CustomerNotFoundException.class)
                .hasMessage("Customer not found");

        verify(customerSubscriptionRepository, never()).findActiveSubscription(any(), any());
    }

    @Test
    @DisplayName("Should throw NoActiveSubscriptionException when customer has no active subscription")
    void should_throw_no_active_subscription_exception_when_calculate_bill_given_customer_without_subscription() {
        UsageRequest request = UsageRequest.builder()
                .customerId("CUST-001")
                .modelId(MODEL_ID)
                .promptTokens(1000)
                .completionTokens(500)
                .build();

        when(customerRepository.findById("CUST-001")).thenReturn(Optional.of(customer));
        when(customerSubscriptionRepository.findActiveSubscription(eq("CUST-001"), any(LocalDate.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> billingService.calculateBill(request))
                .isInstanceOf(NoActiveSubscriptionException.class)
                .hasMessage("No active subscription found");

        verify(billRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should use correct month boundaries for quota aggregation")
    void should_use_correct_month_boundaries_when_calculate_bill_given_mid_month_request() {
        UsageRequest request = UsageRequest.builder()
                .customerId("CUST-001")
                .modelId(MODEL_ID)
                .promptTokens(1000)
                .completionTokens(500)
                .build();

        when(customerRepository.findById("CUST-001")).thenReturn(Optional.of(customer));
        when(customerSubscriptionRepository.findActiveSubscription(eq("CUST-001"), any(LocalDate.class)))
                .thenReturn(Optional.of(subscription));
        when(modelPricingRepository.findByPlanIdAndModelId("PLAN-STARTER", MODEL_ID))
                .thenReturn(Optional.of(modelPricing));
        when(billingStrategyFactory.getStrategy(PlanType.STANDARD)).thenReturn(new StandardBillingStrategy());
        when(billRepository.sumIncludedTokensUsedForMonth(eq("CUST-001"), any(), any())).thenReturn(0);
        when(billRepository.save(any(Bill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        billingService.calculateBill(request);

        ArgumentCaptor<LocalDateTime> monthStartCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> monthEndCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        verify(billRepository).sumIncludedTokensUsedForMonth(
                eq("CUST-001"),
                monthStartCaptor.capture(),
                monthEndCaptor.capture()
        );

        LocalDateTime monthStart = monthStartCaptor.getValue();
        LocalDateTime monthEnd = monthEndCaptor.getValue();

        assertThat(monthStart.getDayOfMonth()).isEqualTo(1);
        assertThat(monthStart.getHour()).isEqualTo(0);
        assertThat(monthStart.getMinute()).isEqualTo(0);
        assertThat(monthStart.getSecond()).isEqualTo(0);

        assertThat(monthEnd.getDayOfMonth()).isEqualTo(1);
        assertThat(monthEnd.getHour()).isEqualTo(0);
        assertThat(monthEnd.getMinute()).isEqualTo(0);
        assertThat(monthEnd.getSecond()).isEqualTo(0);
        assertThat(monthEnd.getMonth()).isEqualTo(monthStart.getMonth().plus(1));
    }

    @Test
    @DisplayName("Should return bill with split charges when calculating bill for premium plan")
    void should_return_bill_with_split_charges_when_calculate_bill_given_premium_plan_usage() {
        PricingPlan premiumPlan = PricingPlan.builder()
                .id("PLAN-PREMIUM")
                .name("Premium Plan")
                .planType(PlanType.PREMIUM)
                .monthlyQuota(0)
                .overageRatePer1k(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .build();

        CustomerSubscription premiumSubscription = CustomerSubscription.builder()
                .id(UUID.randomUUID())
                .customerId("CUST-PREMIUM")
                .plan(premiumPlan)
                .effectiveFrom(LocalDate.of(2026, 1, 1))
                .effectiveTo(null)
                .createdAt(LocalDateTime.now())
                .build();

        ModelPricing premiumModelPricing = ModelPricing.builder()
                .id(UUID.randomUUID())
                .planId("PLAN-PREMIUM")
                .modelId("reasoning-model")
                .promptRatePer1k(new BigDecimal("0.03"))
                .completionRatePer1k(new BigDecimal("0.06"))
                .createdAt(LocalDateTime.now())
                .build();

        Customer premiumCustomer = Customer.builder()
                .id("CUST-PREMIUM")
                .name("Premium Customer")
                .createdAt(LocalDateTime.now())
                .build();

        UsageRequest request = UsageRequest.builder()
                .customerId("CUST-PREMIUM")
                .modelId("reasoning-model")
                .promptTokens(10000)
                .completionTokens(20000)
                .build();

        when(customerRepository.findById("CUST-PREMIUM")).thenReturn(Optional.of(premiumCustomer));
        when(customerSubscriptionRepository.findActiveSubscription(eq("CUST-PREMIUM"), any(LocalDate.class)))
                .thenReturn(Optional.of(premiumSubscription));
        when(modelPricingRepository.findByPlanIdAndModelId("PLAN-PREMIUM", "reasoning-model"))
                .thenReturn(Optional.of(premiumModelPricing));
        when(billingStrategyFactory.getStrategy(PlanType.PREMIUM)).thenReturn(new PremiumBillingStrategy());
        when(billRepository.save(any(Bill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Bill result = billingService.calculateBill(request);

        assertThat(result.getIncludedTokensUsed()).isEqualTo(0);
        assertThat(result.getOverageTokens()).isEqualTo(0);
        assertThat(result.getPromptCharge()).isEqualByComparingTo(new BigDecimal("0.30"));
        assertThat(result.getCompletionCharge()).isEqualByComparingTo(new BigDecimal("1.20"));
        assertThat(result.getTotalCharge()).isEqualByComparingTo(new BigDecimal("1.50"));

        verify(billingStrategyFactory).getStrategy(PlanType.PREMIUM);
        verify(billRepository, never()).sumIncludedTokensUsedForMonth(any(), any(), any());
    }

    @Test
    @DisplayName("Should throw ModelPricingNotFoundException when model pricing is not configured")
    void should_throw_model_pricing_not_found_exception_when_calculate_bill_given_unknown_model() {
        UsageRequest request = UsageRequest.builder()
                .customerId("CUST-001")
                .modelId("unknown-model")
                .promptTokens(1000)
                .completionTokens(500)
                .build();

        when(customerRepository.findById("CUST-001")).thenReturn(Optional.of(customer));
        when(customerSubscriptionRepository.findActiveSubscription(eq("CUST-001"), any(LocalDate.class)))
                .thenReturn(Optional.of(subscription));
        when(modelPricingRepository.findByPlanIdAndModelId("PLAN-STARTER", "unknown-model"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> billingService.calculateBill(request))
                .isInstanceOf(ModelPricingNotFoundException.class)
                .hasMessageContaining("unknown-model");

        verify(billRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should not query quota usage when calculating bill for premium plan with zero quota")
    void should_return_zero_remaining_quota_when_calculate_bill_given_premium_plan() {
        PricingPlan premiumPlan = PricingPlan.builder()
                .id("PLAN-PREMIUM")
                .name("Premium Plan")
                .planType(PlanType.PREMIUM)
                .monthlyQuota(0)
                .overageRatePer1k(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .build();

        CustomerSubscription premiumSubscription = CustomerSubscription.builder()
                .id(UUID.randomUUID())
                .customerId("CUST-PREMIUM")
                .plan(premiumPlan)
                .effectiveFrom(LocalDate.of(2026, 1, 1))
                .effectiveTo(null)
                .createdAt(LocalDateTime.now())
                .build();

        ModelPricing premiumModelPricing = ModelPricing.builder()
                .id(UUID.randomUUID())
                .planId("PLAN-PREMIUM")
                .modelId("fast-model")
                .promptRatePer1k(new BigDecimal("0.01"))
                .completionRatePer1k(new BigDecimal("0.02"))
                .createdAt(LocalDateTime.now())
                .build();

        Customer premiumCustomer = Customer.builder()
                .id("CUST-PREMIUM")
                .name("Premium Customer")
                .createdAt(LocalDateTime.now())
                .build();

        UsageRequest request = UsageRequest.builder()
                .customerId("CUST-PREMIUM")
                .modelId("fast-model")
                .promptTokens(5000)
                .completionTokens(3000)
                .build();

        when(customerRepository.findById("CUST-PREMIUM")).thenReturn(Optional.of(premiumCustomer));
        when(customerSubscriptionRepository.findActiveSubscription(eq("CUST-PREMIUM"), any(LocalDate.class)))
                .thenReturn(Optional.of(premiumSubscription));
        when(modelPricingRepository.findByPlanIdAndModelId("PLAN-PREMIUM", "fast-model"))
                .thenReturn(Optional.of(premiumModelPricing));
        when(billingStrategyFactory.getStrategy(PlanType.PREMIUM)).thenReturn(new PremiumBillingStrategy());
        when(billRepository.save(any(Bill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        billingService.calculateBill(request);

        verify(billRepository, never()).sumIncludedTokensUsedForMonth(any(), any(), any());
        verify(billingStrategyFactory).getStrategy(PlanType.PREMIUM);
    }
}
