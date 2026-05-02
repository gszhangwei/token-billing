package org.tw.token_billing.service;

import org.tw.token_billing.dto.BillResponse;
import org.tw.token_billing.dto.UsageRequest;
import org.tw.token_billing.entity.Bill;
import org.tw.token_billing.entity.Customer;
import org.tw.token_billing.entity.CustomerSubscription;
import org.tw.token_billing.repository.BillRepository;
import org.tw.token_billing.repository.CustomerSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

@Service
public class UsageService {

    private static final MathContext MATH_CONTEXT = new MathContext(10, RoundingMode.HALF_EVEN);
    private static final String CURRENCY_USD = "USD";

    private final CustomerSubscriptionRepository subscriptionRepository;
    private final BillRepository billRepository;

    public UsageService(CustomerSubscriptionRepository subscriptionRepository, BillRepository billRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.billRepository = billRepository;
    }

    @Transactional
    public BillResponse calculateBill(UsageRequest request) {
        // Validate customer exists and has active subscription
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        CustomerSubscription subscription = subscriptionRepository
            .findActiveSubscription(request.getCustomerId(), today)
            .orElseThrow(() -> new CustomerNotFoundException(request.getCustomerId()));

        Customer customer = subscription.getCustomer();
        int quota = subscription.getPricingPlan().getMonthlyQuota();
        BigDecimal overageRatePer1k = subscription.getPricingPlan().getOverageRatePer1k();

        // Calculate total tokens
        int totalTokens = request.getPromptTokens() + request.getCompletionTokens();

        // Get current month usage
        Instant monthStart = today.with(TemporalAdjusters.firstDayOfMonth())
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant();
        long currentMonthUsage = billRepository.sumTotalTokensByCustomerIdAndMonthStart(
            request.getCustomerId(), monthStart);

        // Calculate tokens from quota and overage
        long availableQuota = Math.max(0L, (long) quota - currentMonthUsage);
        int tokensFromQuota = (int) Math.min((long) totalTokens, availableQuota);
        int overageTokens = totalTokens - tokensFromQuota;

        // Calculate charge: (overageTokens / 1000) × overageRatePer1k
        BigDecimal charge = BigDecimal.valueOf(overageTokens)
            .divide(BigDecimal.valueOf(1000), MATH_CONTEXT)
            .multiply(overageRatePer1k)
            .setScale(2, RoundingMode.HALF_EVEN);

        // Create and save the bill
        Instant calculatedAt = Instant.now();
        Bill bill = new Bill(
            UUID.randomUUID(),
            customer,
            request.getPromptTokens(),
            request.getCompletionTokens(),
            totalTokens,
            tokensFromQuota,
            overageTokens,
            charge,
            calculatedAt
        );
        billRepository.save(bill);

        // Build response in camelCase
        return new BillResponse(
            bill.getId(),
            customer.getId(),
            bill.getPromptTokens(),
            bill.getCompletionTokens(),
            bill.getTotalTokens(),
            bill.getIncludedTokensUsed(),
            bill.getOverageTokens(),
            bill.getTotalCharge(),
            CURRENCY_USD,
            bill.getCalculatedAt()
        );
    }

    public static class CustomerNotFoundException extends RuntimeException {
        public CustomerNotFoundException(String customerId) {
            super("Active subscription not found for customer: " + customerId);
        }
    }
}