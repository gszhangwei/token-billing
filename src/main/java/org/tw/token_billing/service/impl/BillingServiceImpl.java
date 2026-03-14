package org.tw.token_billing.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tw.token_billing.domain.Bill;
import org.tw.token_billing.domain.CustomerSubscription;
import org.tw.token_billing.domain.PricingPlan;
import org.tw.token_billing.dto.UsageRequest;
import org.tw.token_billing.exception.CustomerNotFoundException;
import org.tw.token_billing.exception.NoActiveSubscriptionException;
import org.tw.token_billing.repository.BillRepository;
import org.tw.token_billing.repository.CustomerRepository;
import org.tw.token_billing.repository.CustomerSubscriptionRepository;
import org.tw.token_billing.service.BillingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class BillingServiceImpl implements BillingService {

    private final CustomerRepository customerRepository;
    private final CustomerSubscriptionRepository customerSubscriptionRepository;
    private final BillRepository billRepository;

    @Override
    public Bill calculateBill(UsageRequest request) {
        String customerId = request.getCustomerId();

        validateCustomerExists(customerId);
        PricingPlan plan = resolveActivePricingPlan(customerId);
        int remainingQuota = calculateRemainingQuota(customerId, plan);

        Bill bill = Bill.create(
                customerId,
                request.getPromptTokens(),
                request.getCompletionTokens(),
                remainingQuota,
                plan.getOverageRatePer1k()
        );

        log.info("Calculated bill for customer {}: totalTokens={}, includedTokensUsed={}, overageTokens={}, totalCharge={}",
                customerId, bill.getTotalTokens(), bill.getIncludedTokensUsed(),
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

    private int calculateRemainingQuota(String customerId, PricingPlan plan) {
        LocalDate currentDate = LocalDate.now(ZoneOffset.UTC);
        LocalDateTime monthStart = currentDate.withDayOfMonth(1).atStartOfDay();
        LocalDateTime monthEnd = currentDate.plusMonths(1).withDayOfMonth(1).atStartOfDay();

        Integer currentMonthUsage = billRepository.sumIncludedTokensUsedForMonth(customerId, monthStart, monthEnd);
        return plan.getMonthlyQuota() - currentMonthUsage;
    }
}
