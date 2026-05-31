package org.tw.token_billing.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.tw.token_billing.repository.BillRepository;
import org.tw.token_billing.repository.CustomerRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@SpringBootTest
@AutoConfigureMockMvc
class ConcurrencyIntegrationTest {

    @MockBean
    private JwtDecoder jwtDecoder;

    private static final Logger log = LoggerFactory.getLogger(ConcurrencyIntegrationTest.class);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM bills");
    }

    private static String body(String customerId, int prompt, int completion) {
        return "{\"customerId\": \"" + customerId + "\", \"promptTokens\": " + prompt
                + ", \"completionTokens\": " + completion + "}";
    }

    @Test
    void concurrentRequestsOnSameCustomer_serializesCorrectlyAndNoOverageAnomaly() throws Exception {
        String customerId = "CUST-001"; // monthlyQuota: 100,000, overageRatePer1k: 0.0200
        int numThreads = 35;
        int promptTokens = 2000;
        int completionTokens = 1000; // total per request = 3000
        int quota = 100000;
        double overageRatePer1k = 0.0200;

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<CompletableFuture<MvcResult>> futures = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            CompletableFuture<MvcResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return mockMvc.perform(post("/api/usage").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_billing:write")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(customerId, promptTokens, completionTokens)))
                            .andExpect(status().isCreated())
                            .andReturn();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();

        long billCount = billRepository.count();
        assertThat(billCount).isEqualTo(numThreads);

        Integer sumTotalTokens = jdbcTemplate.queryForObject(
            "SELECT SUM(total_tokens) FROM bills WHERE customer_id = ?", Integer.class, customerId);
        Integer sumIncludedUsed = jdbcTemplate.queryForObject(
            "SELECT SUM(included_tokens_used) FROM bills WHERE customer_id = ?", Integer.class, customerId);
        Integer sumOverage = jdbcTemplate.queryForObject(
            "SELECT SUM(overage_tokens) FROM bills WHERE customer_id = ?", Integer.class, customerId);
        Double sumCharge = jdbcTemplate.queryForObject(
            "SELECT SUM(total_charge) FROM bills WHERE customer_id = ?", Double.class, customerId);

        // 動態計算預期結果
        int totalRequestedTokens = numThreads * (promptTokens + completionTokens);
        int expectedIncludedUsed = Math.min(totalRequestedTokens, quota);
        int expectedOverage = Math.max(0, totalRequestedTokens - quota);
        double expectedCharge = (expectedOverage / 1000.0) * overageRatePer1k;

        assertThat(sumTotalTokens).isEqualTo(totalRequestedTokens);
        assertThat(sumIncludedUsed).isEqualTo(expectedIncludedUsed);
        assertThat(sumOverage).isEqualTo(expectedOverage);
        assertThat(sumCharge).isEqualTo(expectedCharge);
    }

    @Test
    void lockTimeout_returns503ServiceUnavailable() throws Exception {
        String customerId = "CUST-001";

        CountDownLatch lockAcquiredLatch = new CountDownLatch(1);
        CountDownLatch releaseLockLatch = new CountDownLatch(1);

        CompletableFuture<Void> locker = CompletableFuture.runAsync(() -> {
            transactionTemplate.execute(status -> {
                log.info("Locker acquiring lock on {}", customerId);
                customerRepository.findByIdForUpdate(customerId).orElseThrow();
                log.info("Locker locked {}. Triggering countdown...", customerId);
                lockAcquiredLatch.countDown();
                try {
                    // 等待主執行緒釋放鎖，最多等 10 秒防死鎖
                    releaseLockLatch.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                log.info("Locker releasing lock on {}", customerId);
                return null;
            });
        });

        // 等待 locker 確定獲取鎖，最多等 5 秒
        boolean acquired = lockAcquiredLatch.await(5, TimeUnit.SECONDS);
        assertThat(acquired).isTrue();

        log.info("Main thread performing request which should timeout...");
        long startTime = System.currentTimeMillis();

        try {
            MvcResult result = mockMvc.perform(post("/api/usage").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_billing:write")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(customerId, 1000, 500)))
                    .andExpect(status().isServiceUnavailable())
                    .andReturn();

            long duration = System.currentTimeMillis() - startTime;
            log.info("Request failed with 503 as expected in {}ms", duration);

            String json = result.getResponse().getContentAsString();
            JsonNode problemDetail = objectMapper.readTree(json);

            assertThat(problemDetail.get("status").asInt()).isEqualTo(503);
            assertThat(problemDetail.get("title").asText()).isEqualTo("Concurrent billing in progress, retry later");
            assertThat(problemDetail.get("detail").asText()).isEqualTo("Concurrent billing in progress, retry later");
        } finally {
            // 無論測試結果如何，必須釋放鎖
            releaseLockLatch.countDown();
        }

        locker.join();
    }
}
