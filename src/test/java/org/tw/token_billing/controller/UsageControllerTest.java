package org.tw.token_billing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.tw.token_billing.dto.BillResponse;
import org.tw.token_billing.exception.CustomerNotFoundException;
import org.tw.token_billing.exception.GlobalExceptionHandler;
import org.tw.token_billing.service.BillingService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UsageController.class)
@Import(GlobalExceptionHandler.class)
class UsageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BillingService billingService;

    @Test
    void should_return_404_when_customer_not_found() throws Exception {
        when(billingService.submitUsage(any())).thenThrow(new CustomerNotFoundException());

        mockMvc.perform(post("/api/usage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"UNKNOWN","promptTokens":100,"completionTokens":50}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Customer not found"));
    }

    @Test
    void should_return_400_when_token_count_negative() throws Exception {
        mockMvc.perform(post("/api/usage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"CUST-001","promptTokens":-1,"completionTokens":50}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Token count cannot be negative"));
    }

    @Test
    void should_return_201_with_bill_details() throws Exception {
        UUID billId = UUID.randomUUID();
        Instant calculatedAt = Instant.parse("2026-05-27T12:00:00Z");

        when(billingService.submitUsage(any())).thenReturn(new BillResponse(
                billId, "CUST-001", 30_000, 30_000, 0, new BigDecimal("0.00"), calculatedAt));

        mockMvc.perform(post("/api/usage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"CUST-001","promptTokens":20000,"completionTokens":10000}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(billId.toString()))
                .andExpect(jsonPath("$.customerId").value("CUST-001"))
                .andExpect(jsonPath("$.totalTokens").value(30_000))
                .andExpect(jsonPath("$.includedTokensUsed").value(30_000))
                .andExpect(jsonPath("$.overageTokens").value(0))
                .andExpect(jsonPath("$.totalCharge").value(0.00))
                .andExpect(jsonPath("$.calculatedAt").value("2026-05-27T12:00:00Z"));
    }
}
