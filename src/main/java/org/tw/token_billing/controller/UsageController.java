package org.tw.token_billing.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.tw.token_billing.dto.BillResponse;
import org.tw.token_billing.dto.BillResult;
import org.tw.token_billing.dto.UsageRequest;
import org.tw.token_billing.service.UsageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

@RestController
@RequestMapping("/api")
@Validated
@Tag(name = "Usage", description = "Token usage billing operations")
public class UsageController {

    private final UsageService usageService;

    public UsageController(UsageService usageService) {
        this.usageService = usageService;
    }

    @Operation(
        summary = "Submit token usage and calculate bill",
        description = "Submits prompt and completion token counts for a customer. Consumes monthly quota first, " +
            "then charges overage at the plan rate. Idempotent when Idempotency-Key header is provided."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "New bill created",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = BillResponse.class))),
        @ApiResponse(responseCode = "200", description = "Idempotency replay — existing bill returned",
            headers = @Header(name = "Idempotent-Replayed", description = "Present and true on replay",
                schema = @Schema(type = "string", example = "true")),
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = BillResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request (missing/invalid fields or Idempotency-Key format)",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid Bearer token",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "403", description = "Token lacks billing:write scope",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "Customer not found",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "409", description = "Customer has no active subscription",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "422", description = "Idempotency-Key reused with different payload",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error (e.g. data integrity)",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "503", description = "Concurrent billing in progress — retry later",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/usage")
    public ResponseEntity<BillResponse> submitUsage(
            @Valid @RequestBody UsageRequest request,
            @Parameter(description = "Idempotency key (8–255 alphanumeric/dash/underscore chars). " +
                "Replays return the original bill with HTTP 200.",
                example = "req-abc-12345678")
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
