package org.tw.token_billing.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ObservabilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void actuatorHealthIsReachableWithoutAuth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
    }

    @Test
    void actuatorPrometheusIsReachableWithoutAuth() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk());
    }

    @Test
    void allFourCustomMetricsAppearInPrometheusAfterBillingRequest() throws Exception {
        // CUST-001 is on PLAN-STARTER (100k token quota). Submitting 200k tokens triggers overage,
        // ensuring billing.overage.charge is recorded in addition to billing.requests.total.
        mockMvc.perform(post("/api/usage")
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_billing:write")))
                .contentType("application/json")
                .content("{\"customerId\": \"CUST-001\", \"promptTokens\": 200000, \"completionTokens\": 0}"))
            .andExpect(status().isCreated());

        MvcResult prometheus = mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk())
            .andReturn();

        String body = prometheus.getResponse().getContentAsString();
        assertThat(body).contains("billing_requests_total");
        assertThat(body).contains("billing_overage_charge");
        assertThat(body).contains("billing_lock_contention_total");
        assertThat(body).contains("billing_idempotency_replay_total");
    }
}
