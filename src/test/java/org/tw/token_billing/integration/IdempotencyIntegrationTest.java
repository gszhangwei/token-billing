package org.tw.token_billing.integration;

import org.tw.token_billing.repository.BillRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.transaction.annotation.Transactional
class IdempotencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BillRepository billRepository;

    private static String body(int prompt, int completion) {
        return "{\"customerId\": \"CUST-001\", \"promptTokens\": " + prompt
                + ", \"completionTokens\": " + completion + "}";
    }

    @Test
    void firstRequestWithIdempotencyKeyCreatesBill() throws Exception {
        mockMvc.perform(post("/api/usage")
                .header("Idempotency-Key", "first-key-12345678")
                .contentType("application/json")
                .content(body(1000, 500)))
                .andExpect(status().isCreated());
    }

    @Test
    void replayReturnsOriginalBillWith200AndReplayHeaderAndNoNewRow() throws Exception {
        String key = "replay-key-abcdefgh";

        MvcResult first = mockMvc.perform(post("/api/usage")
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .content(body(1000, 500)))
                .andExpect(status().isCreated())
                .andReturn();

        long countAfterFirst = billRepository.count();
        JsonNode firstBody = objectMapper.readTree(first.getResponse().getContentAsString());
        String firstId = firstBody.get("billId").asText();

        MvcResult replay = mockMvc.perform(post("/api/usage")
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .content(body(1000, 500)))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andReturn();

        JsonNode replayBody = objectMapper.readTree(replay.getResponse().getContentAsString());
        assertThat(replayBody.get("billId").asText()).isEqualTo(firstId);
        assertThat(billRepository.count()).isEqualTo(countAfterFirst);
    }

    @Test
    void sameKeyDifferentPayloadReturns422() throws Exception {
        String key = "mismatch-key-1234567";

        mockMvc.perform(post("/api/usage")
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .content(body(1000, 500)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/usage")
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .content(body(2000, 500)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void invalidIdempotencyKeyFormatReturns400() throws Exception {
        mockMvc.perform(post("/api/usage")
                .header("Idempotency-Key", "short")
                .contentType("application/json")
                .content(body(1000, 500)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void absentIdempotencyKeyCreatesNewBillEachTime() throws Exception {
        long before = billRepository.count();

        mockMvc.perform(post("/api/usage")
                .contentType("application/json")
                .content(body(1000, 500)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/usage")
                .contentType("application/json")
                .content(body(1000, 500)))
                .andExpect(status().isCreated());

        assertThat(billRepository.count()).isEqualTo(before + 2);
    }
}
