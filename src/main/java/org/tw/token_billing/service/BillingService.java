package org.tw.token_billing.service;

import org.tw.token_billing.dto.BillResponse;
import org.tw.token_billing.dto.QuotaStatusResponse;
import org.tw.token_billing.dto.UsageRequest;

public interface BillingService {

    BillResponse submitUsage(UsageRequest request);

    QuotaStatusResponse getQuotaStatus(String customerId);
}
