package org.tw.token_billing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.tw.token_billing.dto.BillResponse;
import org.tw.token_billing.dto.UsageRequest;
import org.tw.token_billing.exception.CustomerNotFoundException;
import org.tw.token_billing.exception.MultipleActiveSubscriptionsException;
import org.tw.token_billing.exception.NoActiveSubscriptionException;
import org.tw.token_billing.service.UsageService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UsageController.class)
class UsageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UsageService usageService;

    @Test
    void submitUsage_validRequest_returns201WithBillResponse() throws Exception {
        UUID billId = UUID.randomUUID();
        // Fixed UTC instant with non-zero millis to verify ISO-8601 Z formatting
        Instant calculatedAt = Instant.parse("2026-05-02T13:30:45.123Z");
        BillResponse response = new BillResponse(
            billId, "CUST-001", 25000, 25000, 50000, 20000, 30000,
            new BigDecimal("0.60"), "USD", calculatedAt);
        when(usageService.calculateBill(any(UsageRequest.class))).thenReturn(response);

        UsageRequest request = new UsageRequest("CUST-001", 25000, 25000);

        mockMvc.perform(post("/api/usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.billId").value(billId.toString()))
            .andExpect(jsonPath("$.customerId").value("CUST-001"))
            .andExpect(jsonPath("$.promptTokens").value(25000))
            .andExpect(jsonPath("$.completionTokens").value(25000))
            .andExpect(jsonPath("$.totalTokens").value(50000))
            .andExpect(jsonPath("$.tokensFromQuota").value(20000))
            .andExpect(jsonPath("$.overageTokens").value(30000))
            .andExpect(jsonPath("$.totalCharge").isNumber())
            .andExpect(jsonPath("$.currency").value("USD"))
            // AC7: calculatedAt is ISO-8601 UTC with Z suffix
            .andExpect(jsonPath("$.calculatedAt").value(endsWith("Z")))
            // AC7: totalCharge serialized as a number with 2dp (BigDecimal scale preserved)
            .andExpect(content().string(containsString("\"totalCharge\":0.60")));
    }

    // AC1: 404 - Customer not found
    @Test
    void submitUsage_customerNotFound_returns404WithRfc7807() throws Exception {
        when(usageService.calculateBill(any(UsageRequest.class)))
            .thenThrow(new CustomerNotFoundException("CUST-MISSING"));

        UsageRequest request = new UsageRequest("CUST-MISSING", 100, 100);

        mockMvc.perform(post("/api/usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.title").value("Customer not found"))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.detail").value(containsString("CUST-MISSING")))
            .andExpect(jsonPath("$.instance").value("/api/usage"));
    }

    // AC2: 400 - Invalid customer ID format
    @Test
    void submitUsage_invalidCustomerIdFormat_returns400WithRfc7807() throws Exception {
        // Invalid characters (special chars not allowed)
        UsageRequest request = new UsageRequest("CUST@INVALID!", 100, 100);

        mockMvc.perform(post("/api/usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.status").value(400));
    }

    // AC2: 400 - Customer ID too long  
    @Test
    void submitUsage_customerIdTooLong_returns400WithRfc7807() throws Exception {
        // Customer ID exceeds 50 characters
        String longCustomerId = "CUST-" + "A".repeat(50);
        UsageRequest request = new UsageRequest(longCustomerId, 100, 100);

        mockMvc.perform(post("/api/usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.status").value(400));
    }

    // AC2: 400 - Negative token count
    @Test
    void submitUsage_negativePromptTokens_returns400WithRfc7807() throws Exception {
        UsageRequest request = new UsageRequest("CUST-001", -1, 1000);

        mockMvc.perform(post("/api/usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.title").value("Token count cannot be negative"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").value("Token count cannot be negative"))
            .andExpect(jsonPath("$.instance").value("/api/usage"));
    }

    // AC2: 400 - Token count overflow guard (exceeds max)
    @Test
    void submitUsage_tokenCountExceedsMax_returns400WithRfc7807() throws Exception {
        // Exceeds 2_000_000_000
        UsageRequest request = new UsageRequest("CUST-001", 2000000001, 100);

        mockMvc.perform(post("/api/usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.status").value(400));
    }

    // AC2: 400 - Missing required field
    @Test
    void submitUsage_missingPromptTokens_returns400WithRfc7807() throws Exception {
        String body = "{\"customerId\":\"CUST-001\",\"completionTokens\":100}";

        mockMvc.perform(post("/api/usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.title").value("Invalid request body"))
            .andExpect(jsonPath("$.status").value(400));
    }

    // Validation order test: body parsing before field validation
    @Test
    void submitUsage_emptyRequestBody_returns400WithRfc7807() throws Exception {
        String body = "{}";

        mockMvc.perform(post("/api/usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.status").value(400));
    }

    // AC4: 400 - Unknown extra field rejected via fail-on-unknown-properties
    @Test
    void submitUsage_unknownExtraField_returns400WithRfc7807() throws Exception {
        String body = "{\"customerId\":\"CUST-001\",\"promptTokens\":100,"
                + "\"completionTokens\":100,\"unknownField\":\"value\"}";

        mockMvc.perform(post("/api/usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.title").value("Invalid request body"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.instance").value("/api/usage"));

        verify(usageService, never()).calculateBill(any(UsageRequest.class));
    }

    // AC9: SRS-F-6 sequence — body/field validation MUST short-circuit before
    // customer lookup. If the body fails validation, the service must not be called
    // even when the customerId would otherwise resolve to a 404.
    @Test
    void submitUsage_invalidBody_shortCircuitsBeforeCustomerLookup() throws Exception {
        // Stub: if the service were ever called, it would return 404. We must see 400 instead.
        when(usageService.calculateBill(any(UsageRequest.class)))
            .thenThrow(new CustomerNotFoundException("CUST-MISSING"));

        // customerId fails the regex AND would map to a non-existent customer
        UsageRequest request = new UsageRequest("BAD@ID!", 100, 100);

        mockMvc.perform(post("/api/usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.title").value("Invalid customer ID format"))
            .andExpect(jsonPath("$.status").value(400));

        verify(usageService, never()).calculateBill(any(UsageRequest.class));
    }

    // AC6: 409 - Customer exists but has no active subscription (RFC 7807)
    @Test
    void submitUsage_noActiveSubscription_returns409WithRfc7807() throws Exception {
        when(usageService.calculateBill(any(UsageRequest.class)))
            .thenThrow(new NoActiveSubscriptionException("CUST-NOSUB"));

        UsageRequest request = new UsageRequest("CUST-NOSUB", 100, 100);

        mockMvc.perform(post("/api/usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.title").value("No active subscription"))
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.detail").value(containsString("CUST-NOSUB")))
            .andExpect(jsonPath("$.instance").value("/api/usage"));
    }

    // SRS-F-7: 500 - Multiple active subscriptions (data integrity error, RFC 7807)
    @Test
    void submitUsage_multipleActiveSubscriptions_returns500WithRfc7807() throws Exception {
        when(usageService.calculateBill(any(UsageRequest.class)))
            .thenThrow(new MultipleActiveSubscriptionsException("CUST-MULTI"));

        UsageRequest request = new UsageRequest("CUST-MULTI", 100, 100);

        mockMvc.perform(post("/api/usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.title").value("Data integrity error"))
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.detail").value(containsString("CUST-MULTI")))
            .andExpect(jsonPath("$.instance").value("/api/usage"));
    }
}
