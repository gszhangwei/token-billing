package org.tw.token_billing.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.tw.token_billing.dto.QuotaStatusResponse;
import org.tw.token_billing.exception.CustomerNotFoundException;
import org.tw.token_billing.exception.GlobalExceptionHandler;
import org.tw.token_billing.service.BillingService;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QuotaController.class)
@Import(GlobalExceptionHandler.class)
class QuotaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BillingService billingService;

    @Test
    void should_return_404_when_customer_not_found() throws Exception {
        when(billingService.getQuotaStatus("UNKNOWN")).thenThrow(new CustomerNotFoundException());

        mockMvc.perform(get("/api/quota/UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Customer not found"));
    }

    @Test
    void should_return_200_with_quota_snapshot() throws Exception {
        when(billingService.getQuotaStatus("CUST-001")).thenReturn(new QuotaStatusResponse(
                "CUST-001", 100_000, 60_000L, 40_000, new BigDecimal("0.02")));

        mockMvc.perform(get("/api/quota/CUST-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("CUST-001"))
                .andExpect(jsonPath("$.monthlyQuota").value(100_000))
                .andExpect(jsonPath("$.tokensUsedThisMonth").value(60_000))
                .andExpect(jsonPath("$.remainingQuota").value(40_000))
                .andExpect(jsonPath("$.overageRatePer1k").value(0.02));
    }
}
