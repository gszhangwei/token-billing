package org.tw.token_billing.controller;

import org.tw.token_billing.dto.BillResponse;
import org.tw.token_billing.dto.BillResult;
import org.tw.token_billing.dto.UsageRequest;
import org.tw.token_billing.service.UsageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

@RestController
@RequestMapping("/api")
@Validated
public class UsageController {

    private final UsageService usageService;

    public UsageController(UsageService usageService) {
        this.usageService = usageService;
    }

    @PostMapping("/usage")
    public ResponseEntity<BillResponse> submitUsage(
            @Valid @RequestBody UsageRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Pattern(regexp = "^[A-Za-z0-9_\\-]{8,255}$",
                    message = "Invalid Idempotency-Key format")
            String idempotencyKey) {

        BillResult result = usageService.calculateBill(request, idempotencyKey);

        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (result.replayed()) {
            builder.header("Idempotent-Replayed", "true");
        }
        return builder.body(result.body());
    }
}