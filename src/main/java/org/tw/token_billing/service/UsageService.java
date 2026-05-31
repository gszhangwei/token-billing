package org.tw.token_billing.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tw.token_billing.dto.BillResponse;
import org.tw.token_billing.dto.BillResult;
import org.tw.token_billing.dto.UsageRequest;
import org.tw.token_billing.entity.Bill;
import org.tw.token_billing.entity.Customer;
import org.tw.token_billing.entity.CustomerSubscription;
import org.tw.token_billing.exception.ConcurrentBillingException;
import org.tw.token_billing.exception.CustomerNotFoundException;
import org.tw.token_billing.exception.IdempotencyKeyMismatchException;
import org.tw.token_billing.exception.MultipleActiveSubscriptionsException;
import org.tw.token_billing.exception.NoActiveSubscriptionException;
import org.tw.token_billing.repository.BillRepository;
import org.tw.token_billing.repository.CustomerRepository;
import org.tw.token_billing.repository.CustomerSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

@Service
public class UsageService {

    private static final Logger log = LoggerFactory.getLogger(UsageService.class);
    private static final MathContext MATH_CONTEXT = new MathContext(10, RoundingMode.HALF_EVEN);
    private static final String CURRENCY_USD = "USD";

    private final CustomerRepository customerRepository;
    private final CustomerSubscriptionRepository subscriptionRepository;
    private final BillRepository billRepository;

    public UsageService(CustomerRepository customerRepository,
                        CustomerSubscriptionRepository subscriptionRepository,
                        BillRepository billRepository) {
        this.customerRepository = customerRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.billRepository = billRepository;
    }

    @Transactional
    public BillResponse calculateBill(UsageRequest request) {
        BillResult result = calculateBill(request, null);
        return result.body();
    }

    @Transactional
    public BillResult calculateBill(UsageRequest request, String idempotencyKey) {
        // SRS-F-11 / AC2: Acquire pessimistic lock at the very beginning of the transaction to prevent any race condition
        // on subscription lookup, idempotency lookup, and usage calculations.
        Customer customer;
        try {
            customer = customerRepository.findByIdForUpdate(request.getCustomerId())
                .orElseThrow(() -> new CustomerNotFoundException(request.getCustomerId()));
        } catch (org.springframework.dao.PessimisticLockingFailureException | jakarta.persistence.PessimisticLockException e) {
            log.warn("Lock acquisition timeout for customerId={}", request.getCustomerId());
            throw new ConcurrentBillingException(request.getCustomerId(), e);
        }

        // SRS-F-7: active subscription resolution
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<CustomerSubscription> subscriptions = subscriptionRepository
            .findAllActiveSubscriptions(request.getCustomerId(), today);

        if (subscriptions.isEmpty()) {
            throw new NoActiveSubscriptionException(request.getCustomerId());
        }
        if (subscriptions.size() > 1) {
            log.error("Multiple active subscriptions for customerId={}", request.getCustomerId());
            throw new MultipleActiveSubscriptionsException(request.getCustomerId());
        }

        CustomerSubscription subscription = subscriptions.get(0);

        // SRS-F-6 step 6 — idempotency lookup
        if (idempotencyKey != null) {
            List<Bill> hits = billRepository.findActiveIdempotentBills(
                request.getCustomerId(), idempotencyKey);
            if (!hits.isEmpty()) {
                Bill existing = hits.get(0);
                if (payloadMatches(existing, request)) {
                    log.info("Idempotency replay hit customerId={} keyPrefix={}",
                        request.getCustomerId(), prefix8(idempotencyKey));
                    return new BillResult(toResponse(existing), true);
                } else {
                    throw new IdempotencyKeyMismatchException(request.getCustomerId(), idempotencyKey);
                }
            }
        }

        int quota = subscription.getPricingPlan().getMonthlyQuota();
        BigDecimal overageRatePer1k = subscription.getPricingPlan().getOverageRatePer1k();

        int totalTokens = request.getPromptTokens() + request.getCompletionTokens();

        Instant monthStart = today.with(TemporalAdjusters.firstDayOfMonth())
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant();
        long currentMonthUsage = billRepository.sumTotalTokensByCustomerIdAndMonthStart(
            request.getCustomerId(), monthStart);

        long availableQuota = Math.max(0L, (long) quota - currentMonthUsage);
        int tokensFromQuota = (int) Math.min((long) totalTokens, availableQuota);
        int overageTokens = totalTokens - tokensFromQuota;

        BigDecimal charge = BigDecimal.valueOf(overageTokens)
            .divide(BigDecimal.valueOf(1000), MATH_CONTEXT)
            .multiply(overageRatePer1k)
            .setScale(2, RoundingMode.HALF_EVEN);

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
            calculatedAt,
            idempotencyKey
        );
        billRepository.save(bill);

        return new BillResult(toResponse(bill), false);
    }

    private boolean payloadMatches(Bill existing, UsageRequest request) {
        return existing.getPromptTokens().equals(request.getPromptTokens())
            && existing.getCompletionTokens().equals(request.getCompletionTokens());
    }

    private String prefix8(String key) {
        return key.length() > 8 ? key.substring(0, 8) : key;
    }

    private BillResponse toResponse(Bill bill) {
        return new BillResponse(
            bill.getId(),
            bill.getCustomer().getId(),
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
}
