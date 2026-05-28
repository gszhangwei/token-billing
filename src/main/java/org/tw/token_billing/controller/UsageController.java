package org.tw.token_billing.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tw.token_billing.dto.BillResponse;
import org.tw.token_billing.dto.UsageRequest;
import org.tw.token_billing.service.BillingService;

@RestController
@RequestMapping("/api")
public class UsageController {

    private final BillingService billingService;

    public UsageController(BillingService billingService) {
        this.billingService = billingService;
    }

    @PostMapping("/usage")
    public ResponseEntity<BillResponse> submitUsage(@Valid @RequestBody UsageRequest request) {
        BillResponse response = billingService.submitUsage(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
