package org.tw.token_billing.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tw.token_billing.domain.Bill;

class BillResponseTest {

    @Test
    @DisplayName("Should map all fields when converting from Bill")
    void should_map_all_fields_when_from_bill_given_complete_bill() {
        UUID billId = UUID.randomUUID();
        LocalDateTime calculatedAt = LocalDateTime.now();

        Bill bill = Bill.builder()
                .id(billId)
                .customerId("CUST-001")
                .promptTokens(1000)
                .completionTokens(500)
                .totalTokens(1500)
                .includedTokensUsed(1000)
                .overageTokens(500)
                .totalCharge(new BigDecimal("0.01"))
                .calculatedAt(calculatedAt)
                .build();

        BillResponse response = BillResponse.fromBill(bill);

        assertThat(response.getBillId()).isEqualTo(billId);
        assertThat(response.getCustomerId()).isEqualTo("CUST-001");
        assertThat(response.getTotalTokens()).isEqualTo(1500);
        assertThat(response.getIncludedTokensUsed()).isEqualTo(1000);
        assertThat(response.getOverageTokens()).isEqualTo(500);
        assertThat(response.getTotalCharge()).isEqualByComparingTo(new BigDecimal("0.01"));
        assertThat(response.getCalculatedAt()).isEqualTo(calculatedAt);
    }
}
