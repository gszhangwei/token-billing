package org.tw.token_billing.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tw.token_billing.dto.QuotaStatusResponse;
import org.tw.token_billing.service.BillingService;

@RestController
@RequestMapping("/api")
public class QuotaController {

    private final BillingService billingService;

    public QuotaController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping("/quota/{customerId}")
    public ResponseEntity<QuotaStatusResponse> getQuotaStatus(@PathVariable String customerId) {
        return ResponseEntity.ok(billingService.getQuotaStatus(customerId));
    }
}
