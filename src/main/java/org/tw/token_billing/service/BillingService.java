package org.tw.token_billing.service;

import org.tw.token_billing.domain.Bill;
import org.tw.token_billing.dto.UsageRequest;

public interface BillingService {
    Bill calculateBill(UsageRequest request);
}
