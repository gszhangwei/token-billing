package org.tw.token_billing.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tw.token_billing.domain.Bill;
import org.tw.token_billing.domain.BillingContext;
import org.tw.token_billing.domain.CustomerSubscription;
import org.tw.token_billing.domain.ModelPricing;
import org.tw.token_billing.domain.PricingPlan;
import org.tw.token_billing.dto.UsageRequest;
import org.tw.token_billing.exception.CustomerNotFoundException;
import org.tw.token_billing.exception.ModelPricingNotFoundException;
import org.tw.token_billing.exception.NoActiveSubscriptionException;
import org.tw.token_billing.repository.BillRepository;
import org.tw.token_billing.repository.CustomerRepository;
import org.tw.token_billing.repository.CustomerSubscriptionRepository;
import org.tw.token_billing.repository.ModelPricingRepository;
import org.tw.token_billing.service.BillingService;
import org.tw.token_billing.service.strategy.BillingStrategy;
import org.tw.token_billing.service.strategy.BillingStrategyFactory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class BillingServiceImpl implements BillingService {

    private static final int NO_QUOTA = 0;
    private static final int FIRST_DAY_OF_MONTH = 1;
    private static final int ONE_MONTH = 1;

    private final CustomerRepository customerRepository;
    private final CustomerSubscriptionRepository customerSubscriptionRepository;
    private final BillRepository billRepository;
    private final ModelPricingRepository modelPricingRepository;
    private final BillingStrategyFactory billingStrategyFactory;

    @Override
    public Bill calculateBill(UsageRequest request) {
        String customerId = request.getCustomerId();
        String modelId = request.getModelId();

        validateCustomerExists(customerId);
        PricingPlan plan = resolveActivePricingPlan(customerId);
        ModelPricing modelPricing = resolveModelPricing(plan.getId(), modelId);
        int remainingQuota = calculateRemainingQuota(customerId, plan);

        BillingContext context = BillingContext.builder()
                .customerId(customerId)
                .modelId(modelId)
                .promptTokens(request.getPromptTokens())
                .completionTokens(request.getCompletionTokens())
                .remainingQuota(remainingQuota)
                .modelPricing(modelPricing)
                .build();

        BillingStrategy strategy = billingStrategyFactory.getStrategy(plan.getPlanType());
        Bill bill = strategy.calculate(context);

        log.info("Calculated bill for customer {}: model={}, planType={}, totalTokens={}, includedTokensUsed={}, overageTokens={}, totalCharge={}",
                customerId, modelId, plan.getPlanType(), bill.getTotalTokens(), bill.getIncludedTokensUsed(),
                bill.getOverageTokens(), bill.getTotalCharge());

        return billRepository.save(bill);
    }

    private void validateCustomerExists(String customerId) {
        customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
    }

    private PricingPlan resolveActivePricingPlan(String customerId) {
        LocalDate currentDate = LocalDate.now(ZoneOffset.UTC);

        CustomerSubscription subscription = customerSubscriptionRepository
                .findActiveSubscription(customerId, currentDate)
                .orElseThrow(() -> new NoActiveSubscriptionException(customerId));

        return subscription.getPlan();
    }

    private ModelPricing resolveModelPricing(String planId, String modelId) {
        return modelPricingRepository.findByPlanIdAndModelId(planId, modelId)
                .orElseThrow(() -> new ModelPricingNotFoundException(planId, modelId));
    }

    private int calculateRemainingQuota(String customerId, PricingPlan plan) {
        if (plan.getMonthlyQuota() == null || plan.getMonthlyQuota() == NO_QUOTA) {
            return NO_QUOTA;
        }

        LocalDate currentDate = LocalDate.now(ZoneOffset.UTC);
        LocalDateTime monthStart = currentDate.withDayOfMonth(FIRST_DAY_OF_MONTH).atStartOfDay();
        LocalDateTime monthEnd = currentDate.plusMonths(ONE_MONTH).withDayOfMonth(FIRST_DAY_OF_MONTH).atStartOfDay();

        Integer currentMonthUsage = billRepository.sumIncludedTokensUsedForMonth(customerId, monthStart, monthEnd);
        return plan.getMonthlyQuota() - currentMonthUsage;
    }
}
