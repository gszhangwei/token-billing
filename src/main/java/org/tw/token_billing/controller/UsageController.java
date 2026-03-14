package org.tw.token_billing.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tw.token_billing.domain.Bill;
import org.tw.token_billing.dto.BillResponse;
import org.tw.token_billing.dto.UsageRequest;
import org.tw.token_billing.service.BillingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UsageController {

    private final BillingService billingService;

    @PostMapping("/usage")
    public ResponseEntity<BillResponse> submitUsage(@Valid @RequestBody UsageRequest request) {
        Bill bill = billingService.calculateBill(request);
        BillResponse response = BillResponse.fromBill(bill);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
