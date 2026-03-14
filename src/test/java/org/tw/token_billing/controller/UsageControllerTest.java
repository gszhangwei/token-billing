package org.tw.token_billing.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.tw.token_billing.domain.Bill;
import org.tw.token_billing.dto.UsageRequest;
import org.tw.token_billing.exception.CustomerNotFoundException;
import org.tw.token_billing.exception.NoActiveSubscriptionException;
import org.tw.token_billing.service.BillingService;

@WebMvcTest(UsageController.class)
class UsageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BillingService billingService;

    @Test
    @DisplayName("Should return 201 Created with bill response when submitting valid usage request")
    void should_return_201_created_with_bill_response_when_submit_usage_given_valid_request() throws Exception {
        Bill bill = Bill.builder()
                .id(UUID.randomUUID())
                .customerId("CUST-001")
                .promptTokens(1000)
                .completionTokens(500)
                .totalTokens(1500)
                .includedTokensUsed(1500)
                .overageTokens(0)
                .totalCharge(BigDecimal.ZERO)
                .calculatedAt(LocalDateTime.now())
                .build();

        when(billingService.calculateBill(any(UsageRequest.class))).thenReturn(bill);

        mockMvc.perform(post("/api/usage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerId": "CUST-001",
                                    "promptTokens": 1000,
                                    "completionTokens": 500
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.billId").exists())
                .andExpect(jsonPath("$.customerId").value("CUST-001"))
                .andExpect(jsonPath("$.totalTokens").value(1500))
                .andExpect(jsonPath("$.includedTokensUsed").value(1500))
                .andExpect(jsonPath("$.overageTokens").value(0))
                .andExpect(jsonPath("$.totalCharge").value(0))
                .andExpect(jsonPath("$.calculatedAt").exists());

        verify(billingService).calculateBill(any(UsageRequest.class));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when customerId is missing")
    void should_return_400_bad_request_when_submit_usage_given_missing_customer_id() throws Exception {
        mockMvc.perform(post("/api/usage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "promptTokens": 1000,
                                    "completionTokens": 500
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Customer ID is required"));

        verify(billingService, never()).calculateBill(any());
    }

    @Test
    @DisplayName("Should return 400 Bad Request when promptTokens is negative")
    void should_return_400_bad_request_when_submit_usage_given_negative_prompt_tokens() throws Exception {
        mockMvc.perform(post("/api/usage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerId": "CUST-001",
                                    "promptTokens": -100,
                                    "completionTokens": 500
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Token count cannot be negative"));

        verify(billingService, never()).calculateBill(any());
    }

    @Test
    @DisplayName("Should return 400 Bad Request when completionTokens is negative")
    void should_return_400_bad_request_when_submit_usage_given_negative_completion_tokens() throws Exception {
        mockMvc.perform(post("/api/usage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerId": "CUST-001",
                                    "promptTokens": 1000,
                                    "completionTokens": -500
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Token count cannot be negative"));

        verify(billingService, never()).calculateBill(any());
    }

    @Test
    @DisplayName("Should return 400 Bad Request when promptTokens is null")
    void should_return_400_bad_request_when_submit_usage_given_null_prompt_tokens() throws Exception {
        mockMvc.perform(post("/api/usage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerId": "CUST-001",
                                    "completionTokens": 500
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(billingService, never()).calculateBill(any());
    }

    @Test
    @DisplayName("Should return 404 Not Found when customer does not exist")
    void should_return_404_not_found_when_submit_usage_given_non_existent_customer() throws Exception {
        when(billingService.calculateBill(any(UsageRequest.class)))
                .thenThrow(new CustomerNotFoundException("INVALID-CUSTOMER"));

        mockMvc.perform(post("/api/usage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerId": "INVALID-CUSTOMER",
                                    "promptTokens": 1000,
                                    "completionTokens": 500
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Customer not found"));
    }

    @Test
    @DisplayName("Should return 422 Unprocessable Entity when customer has no active subscription")
    void should_return_422_unprocessable_entity_when_submit_usage_given_no_active_subscription() throws Exception {
        when(billingService.calculateBill(any(UsageRequest.class)))
                .thenThrow(new NoActiveSubscriptionException("CUST-NO-SUB"));

        mockMvc.perform(post("/api/usage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerId": "CUST-NO-SUB",
                                    "promptTokens": 1000,
                                    "completionTokens": 500
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("No active subscription found"));
    }
}
