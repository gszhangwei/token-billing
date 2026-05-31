package org.tw.token_billing.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class BillingMetrics {

    private final Counter lockContentionTotal;
    private final Counter idempotencyReplayTotal;
    private final MeterRegistry registry;

    public BillingMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.lockContentionTotal = Counter.builder("billing.lock.contention.total")
            .description("Number of pessimistic lock acquisition failures")
            .register(registry);
        this.idempotencyReplayTotal = Counter.builder("billing.idempotency.replay.total")
            .description("Number of idempotency key replay hits")
            .register(registry);
    }

    public void incrementLockContention() {
        lockContentionTotal.increment();
    }

    public void incrementIdempotencyReplay() {
        idempotencyReplayTotal.increment();
    }

    public void incrementRequests(String customerId, String status) {
        registry.counter("billing.requests.total",
            "customer_id", customerId,
            "status", status
        ).increment();
    }

    public void recordOverageCharge(String customerId, String planId, double chargeUsd) {
        DistributionSummary.builder("billing.overage.charge")
            .description("Overage charge amounts in USD")
            .baseUnit("USD")
            .tag("customer_id", customerId)
            .tag("plan_id", planId)
            .register(registry)
            .record(chargeUsd);
    }
}
