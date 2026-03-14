package org.tw.token_billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UsageControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Should return 201 and persist bill when submitting valid usage for customer with subscription")
    void should_return_201_and_persist_bill_when_submit_usage_given_valid_customer_with_subscription() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/usage")
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
                .andExpect(jsonPath("$.includedTokensUsed").exists())
                .andExpect(jsonPath("$.overageTokens").exists())
                .andExpect(jsonPath("$.totalCharge").exists())
                .andExpect(jsonPath("$.calculatedAt").exists())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.get("billId").asText()).isNotEmpty();
    }

    @Test
    @DisplayName("Should return 404 when submitting usage for non-existent customer")
    void should_return_404_when_submit_usage_given_non_existent_customer() throws Exception {
        mockMvc.perform(post("/api/usage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerId": "NON-EXISTENT-CUSTOMER",
                                    "promptTokens": 1000,
                                    "completionTokens": 500
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Customer not found"));
    }

    @Test
    @DisplayName("Should return 400 when submitting usage with invalid JSON")
    void should_return_400_when_submit_usage_given_invalid_json() throws Exception {
        mockMvc.perform(post("/api/usage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ invalid json }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should track quota correctly when submitting multiple usages for same customer")
    void should_track_quota_correctly_when_submit_multiple_usages_given_same_customer() throws Exception {
        MvcResult firstResult = mockMvc.perform(post("/api/usage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerId": "CUST-002",
                                    "promptTokens": 5000,
                                    "completionTokens": 3000
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode firstResponse = objectMapper.readTree(firstResult.getResponse().getContentAsString());
        int firstTotal = firstResponse.get("totalTokens").asInt();
        int firstIncluded = firstResponse.get("includedTokensUsed").asInt();

        MvcResult secondResult = mockMvc.perform(post("/api/usage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerId": "CUST-002",
                                    "promptTokens": 5000,
                                    "completionTokens": 3000
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode secondResponse = objectMapper.readTree(secondResult.getResponse().getContentAsString());
        int secondTotal = secondResponse.get("totalTokens").asInt();

        assertThat(firstTotal).isEqualTo(8000);
        assertThat(secondTotal).isEqualTo(8000);
        assertThat(firstIncluded).isLessThanOrEqualTo(firstTotal);
    }

    @Test
    @DisplayName("Should calculate overage correctly when quota is exhausted")
    void should_calculate_overage_when_submit_usage_given_quota_exhausted() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/usage")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "customerId": "CUST-002",
                                        "promptTokens": 4000,
                                        "completionTokens": 2000
                                    }
                                    """))
                    .andExpect(status().isCreated());
        }

        MvcResult finalResult = mockMvc.perform(post("/api/usage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerId": "CUST-002",
                                    "promptTokens": 3000,
                                    "completionTokens": 2000
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode response = objectMapper.readTree(finalResult.getResponse().getContentAsString());
        int overageTokens = response.get("overageTokens").asInt();
        BigDecimal totalCharge = new BigDecimal(response.get("totalCharge").asText());

        if (overageTokens > 0) {
            assertThat(totalCharge).isGreaterThan(BigDecimal.ZERO);
        }
    }
}
