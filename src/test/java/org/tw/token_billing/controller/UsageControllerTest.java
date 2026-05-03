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
import org.tw.token_billing.service.UsageService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
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

    @Test
    void submitUsage_negativePromptTokens_returns400() throws Exception {
        UsageRequest request = new UsageRequest("CUST-001", -1, 1000);

        mockMvc.perform(post("/api/usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void submitUsage_blankCustomerId_returns400() throws Exception {
        UsageRequest request = new UsageRequest("", 100, 100);

        mockMvc.perform(post("/api/usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void submitUsage_missingPromptTokens_returns400() throws Exception {
        String body = "{\"customerId\":\"CUST-001\",\"completionTokens\":100}";

        mockMvc.perform(post("/api/usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void submitUsage_noActiveSubscription_returns409() throws Exception {
        when(usageService.calculateBill(any(UsageRequest.class)))
            .thenThrow(new UsageService.NoActiveSubscriptionException("CUST-MISSING"));

        UsageRequest request = new UsageRequest("CUST-MISSING", 100, 100);

        mockMvc.perform(post("/api/usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(
                org.hamcrest.Matchers.containsString("CUST-MISSING")));
    }

    @Test
    void submitUsage_multipleSubscriptions_returns500() throws Exception {
        when(usageService.calculateBill(any(UsageRequest.class)))
            .thenThrow(new UsageService.MultipleSubscriptionsException("CUST-DUP"));

        UsageRequest request = new UsageRequest("CUST-DUP", 100, 100);

        mockMvc.perform(post("/api/usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message").value(
                org.hamcrest.Matchers.containsString("CUST-DUP")));
    }
}
