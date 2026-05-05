package org.tw.token_billing.controller;

import org.tw.token_billing.dto.BillResponse;
import org.tw.token_billing.dto.UsageRequest;
import org.tw.token_billing.service.UsageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
@Validated
public class UsageController {

    private final UsageService usageService;

    public UsageController(UsageService usageService) {
        this.usageService = usageService;
    }

    @PostMapping("/usage")
    public ResponseEntity<BillResponse> submitUsage(@Valid @RequestBody UsageRequest request) {
        BillResponse response = usageService.calculateBill(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}