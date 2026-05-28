package org.tw.token_billing.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tw.token_billing.dto.BillResponse;
import org.tw.token_billing.dto.QuotaStatusResponse;
import org.tw.token_billing.dto.UsageRequest;
import org.tw.token_billing.entity.Bill;
import org.tw.token_billing.entity.CustomerSubscription;
import org.tw.token_billing.entity.PricingPlan;
import org.tw.token_billing.exception.CustomerNotFoundException;
import org.tw.token_billing.exception.NoActiveSubscriptionException;
import org.tw.token_billing.repository.BillRepository;
import org.tw.token_billing.repository.CustomerRepository;
import org.tw.token_billing.repository.CustomerSubscriptionRepository;
import org.tw.token_billing.repository.PricingPlanRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@Slf4j
public class BillingServiceImpl implements BillingService {

    private final CustomerRepository customerRepository;
    private final CustomerSubscriptionRepository customerSubscriptionRepository;
    private final PricingPlanRepository pricingPlanRepository;
    private final BillRepository billRepository;

    public BillingServiceImpl(
            CustomerRepository customerRepository,
            CustomerSubscriptionRepository customerSubscriptionRepository,
            PricingPlanRepository pricingPlanRepository,
            BillRepository billRepository) {
        this.customerRepository = customerRepository;
        this.customerSubscriptionRepository = customerSubscriptionRepository;
        this.pricingPlanRepository = pricingPlanRepository;
        this.billRepository = billRepository;
    }

    @Override
    @Transactional
    public BillResponse submitUsage(UsageRequest request) {
        MonthlyQuotaContext context = resolveMonthlyQuotaContext(request.customerId());

        int totalTokens = request.promptTokens() + request.completionTokens();
        int includedTokensUsed = Math.min(totalTokens, context.remainingQuota());
        int overageTokens = totalTokens - includedTokensUsed;

        BigDecimal totalCharge = context.plan().getOverageRatePer1k()
                .multiply(BigDecimal.valueOf(overageTokens))
                .divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);

        Instant calculatedAt = Instant.now();
        Bill bill = new Bill();
        bill.setId(UUID.randomUUID());
        bill.setCustomerId(request.customerId());
        bill.setPromptTokens(request.promptTokens());
        bill.setCompletionTokens(request.completionTokens());
        bill.setTotalTokens(totalTokens);
        bill.setIncludedTokensUsed(includedTokensUsed);
        bill.setOverageTokens(overageTokens);
        bill.setTotalCharge(totalCharge);
        bill.setCalculatedAt(calculatedAt);

        Bill saved = billRepository.save(bill);
        log.info("Bill created id={} customerId={}", saved.getId(), saved.getCustomerId());

        return BillResponse.from(saved);
    }

    @Override
    public QuotaStatusResponse getQuotaStatus(String customerId) {
        MonthlyQuotaContext context = resolveMonthlyQuotaContext(customerId);
        log.info("Quota status lookup customerId={}", customerId);
        return QuotaStatusResponse.from(
                customerId, context.plan(), context.currentMonthUsage(), context.remainingQuota());
    }

    private MonthlyQuotaContext resolveMonthlyQuotaContext(String customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new CustomerNotFoundException();
        }

        LocalDate asOfDate = LocalDate.now(ZoneOffset.UTC);
        CustomerSubscription subscription = customerSubscriptionRepository
                .findActiveByCustomerId(customerId, asOfDate)
                .orElseThrow(NoActiveSubscriptionException::new);

        PricingPlan plan = pricingPlanRepository
                .findById(subscription.getPlanId())
                .orElseThrow(NoActiveSubscriptionException::new);

        Instant monthStart = asOfDate.withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant monthEnd = asOfDate.plusMonths(1).withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        long currentMonthUsage = billRepository.sumTotalTokensByCustomerIdAndCalculatedAtBetween(
                customerId, monthStart, monthEnd);

        int remainingQuota = Math.max(0, plan.getMonthlyQuota() - (int) currentMonthUsage);
        return new MonthlyQuotaContext(plan, currentMonthUsage, remainingQuota);
    }
}
