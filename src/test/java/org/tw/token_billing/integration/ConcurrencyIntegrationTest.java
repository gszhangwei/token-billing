package org.tw.token_billing.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.tw.token_billing.repository.BillRepository;
import org.tw.token_billing.repository.CustomerRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ConcurrencyIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(ConcurrencyIntegrationTest.class);

    @Container
    static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

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
        String customerId = "CUST-001"; // quota: 100,000, overage_rate_per_1k: 0.0200
        int numThreads = 50;
        int promptTokens = 2000;
        int completionTokens = 1000; // total per request = 3000

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<CompletableFuture<MvcResult>> futures = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            CompletableFuture<MvcResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return mockMvc.perform(post("/api/usage")
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

        assertThat(sumTotalTokens).isEqualTo(150000);
        assertThat(sumIncludedUsed).isEqualTo(100000);
        assertThat(sumOverage).isEqualTo(50000);
        assertThat(sumCharge).isEqualTo(1.00);
    }

    @Test
    void lockTimeout_returns503ServiceUnavailable() throws Exception {
        String customerId = "CUST-001";

        CompletableFuture<Void> locker = CompletableFuture.runAsync(() -> {
            transactionTemplate.execute(status -> {
                log.info("Locker acquiring lock on {}", customerId);
                customerRepository.findByIdForUpdate(customerId).orElseThrow();
                log.info("Locker locked {}. Sleeping for 6s...", customerId);
                try {
                    Thread.sleep(6000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                log.info("Locker releasing lock on {}", customerId);
                return null;
            });
        });

        Thread.sleep(1000);

        log.info("Main thread performing request which should timeout...");
        long startTime = System.currentTimeMillis();

        MvcResult result = mockMvc.perform(post("/api/usage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(customerId, 1000, 500)))
                .andExpect(status().isServiceUnavailable())
                .andReturn();

        long duration = System.currentTimeMillis() - startTime;
        log.info("Request failed with 503 as expected in {}ms", duration);

        assertThat(duration).isGreaterThanOrEqualTo(4500L);

        String json = result.getResponse().getContentAsString();
        JsonNode problemDetail = objectMapper.readTree(json);

        assertThat(problemDetail.get("status").asInt()).isEqualTo(503);
        assertThat(problemDetail.get("title").asText()).isEqualTo("Concurrent billing in progress, retry later");
        assertThat(problemDetail.get("detail").asText()).isEqualTo("Concurrent billing in progress, retry later");

        locker.join();
    }
}
