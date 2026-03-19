package org.tw.token_billing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BillTest {

    @Test
    @DisplayName("Should create bill with correct totals when given valid inputs")
    void should_create_bill_with_correct_totals_when_create_given_valid_inputs() {
        LocalDateTime beforeCreation = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1);

        Bill bill = Bill.createStandard("CUST-001", "fast-model", 1000, 500, 10000, new BigDecimal("0.02"));

        assertThat(bill.getId()).isNotNull();
        assertThat(bill.getCustomerId()).isEqualTo("CUST-001");
        assertThat(bill.getModelId()).isEqualTo("fast-model");
        assertThat(bill.getPromptTokens()).isEqualTo(1000);
        assertThat(bill.getCompletionTokens()).isEqualTo(500);
        assertThat(bill.getTotalTokens()).isEqualTo(1500);
        assertThat(bill.getCalculatedAt()).isNotNull();
        assertThat(bill.getCalculatedAt()).isAfterOrEqualTo(beforeCreation);
    }

    @Test
    @DisplayName("Should create bill with all included when usage is within quota")
    void should_create_bill_with_all_included_when_create_given_usage_within_quota() {
        Bill bill = Bill.createStandard("CUST-001", "fast-model", 1000, 500, 10000, new BigDecimal("0.02"));

        assertThat(bill.getIncludedTokensUsed()).isEqualTo(1500);
        assertThat(bill.getOverageTokens()).isEqualTo(0);
        assertThat(bill.getTotalCharge()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(bill.getPromptCharge()).isNull();
        assertThat(bill.getCompletionCharge()).isNull();
    }

    @Test
    @DisplayName("Should create bill with partial included when usage exceeds quota")
    void should_create_bill_with_partial_included_when_create_given_usage_exceeds_quota() {
        Bill bill = Bill.createStandard("CUST-001", "fast-model", 8000, 5000, 10000, new BigDecimal("0.02"));

        assertThat(bill.getTotalTokens()).isEqualTo(13000);
        assertThat(bill.getIncludedTokensUsed()).isEqualTo(10000);
        assertThat(bill.getOverageTokens()).isEqualTo(3000);
        assertThat(bill.getTotalCharge()).isEqualByComparingTo(new BigDecimal("0.06"));
    }

    @Test
    @DisplayName("Should create bill with all overage when remaining quota is zero")
    void should_create_bill_with_all_overage_when_create_given_zero_remaining_quota() {
        Bill bill = Bill.createStandard("CUST-001", "fast-model", 1000, 500, 0, new BigDecimal("0.02"));

        assertThat(bill.getIncludedTokensUsed()).isEqualTo(0);
        assertThat(bill.getOverageTokens()).isEqualTo(1500);
        assertThat(bill.getTotalCharge()).isEqualByComparingTo(new BigDecimal("0.03"));
    }

    @Test
    @DisplayName("Should create bill with all overage when remaining quota is negative")
    void should_create_bill_with_all_overage_when_create_given_negative_remaining_quota() {
        Bill bill = Bill.createStandard("CUST-001", "fast-model", 1000, 500, -500, new BigDecimal("0.02"));

        assertThat(bill.getIncludedTokensUsed()).isEqualTo(0);
        assertThat(bill.getOverageTokens()).isEqualTo(1500);
        assertThat(bill.getTotalCharge()).isEqualByComparingTo(new BigDecimal("0.03"));
    }

    @Test
    @DisplayName("Should create bill with correct charge precision for fractional calculation")
    void should_create_bill_with_correct_charge_precision_when_create_given_fractional_calculation() {
        Bill bill = Bill.createStandard("CUST-001", "fast-model", 1234, 567, 0, new BigDecimal("0.0234"));

        assertThat(bill.getTotalTokens()).isEqualTo(1801);
        assertThat(bill.getOverageTokens()).isEqualTo(1801);
        assertThat(bill.getTotalCharge().scale()).isEqualTo(2);

        BigDecimal expectedCharge = new BigDecimal("1801")
                .multiply(new BigDecimal("0.0234"))
                .divide(new BigDecimal("1000"), 2, java.math.RoundingMode.HALF_UP);
        assertThat(bill.getTotalCharge()).isEqualByComparingTo(expectedCharge);
    }

    @Test
    @DisplayName("Should create premium bill with split charges")
    void should_create_premium_bill_with_split_charges_when_create_premium_given_valid_inputs() {
        Bill bill = Bill.createPremium("CUST-001", "reasoning-model", 10000, 20000,
                new BigDecimal("0.03"), new BigDecimal("0.06"));

        assertThat(bill.getId()).isNotNull();
        assertThat(bill.getCustomerId()).isEqualTo("CUST-001");
        assertThat(bill.getModelId()).isEqualTo("reasoning-model");
        assertThat(bill.getPromptTokens()).isEqualTo(10000);
        assertThat(bill.getCompletionTokens()).isEqualTo(20000);
        assertThat(bill.getTotalTokens()).isEqualTo(30000);
        assertThat(bill.getIncludedTokensUsed()).isEqualTo(0);
        assertThat(bill.getOverageTokens()).isEqualTo(0);
        assertThat(bill.getPromptCharge()).isEqualByComparingTo(new BigDecimal("0.30"));
        assertThat(bill.getCompletionCharge()).isEqualByComparingTo(new BigDecimal("1.20"));
        assertThat(bill.getTotalCharge()).isEqualByComparingTo(new BigDecimal("1.50"));
    }
}
