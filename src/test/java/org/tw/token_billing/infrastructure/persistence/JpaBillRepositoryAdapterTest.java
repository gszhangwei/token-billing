package org.tw.token_billing.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tw.token_billing.domain.Bill;
import org.tw.token_billing.infrastructure.persistence.entity.BillPO;
import org.tw.token_billing.infrastructure.persistence.mapper.BillMapper;

@ExtendWith(MockitoExtension.class)
class JpaBillRepositoryAdapterTest {

    @Mock
    private SpringDataBillRepository springDataRepository;

    @Mock
    private BillMapper billMapper;

    @InjectMocks
    private JpaBillRepositoryAdapter adapter;

    @Test
    @DisplayName("Should save and return domain bill when saving valid bill")
    void should_save_and_return_domain_bill_when_save_given_valid_bill() {
        UUID billId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        Bill domainBill = Bill.builder()
                .id(billId)
                .customerId("CUST-001")
                .promptTokens(1000)
                .completionTokens(500)
                .totalTokens(1500)
                .includedTokensUsed(1500)
                .overageTokens(0)
                .totalCharge(BigDecimal.ZERO)
                .calculatedAt(now)
                .build();

        BillPO billPO = BillPO.builder()
                .id(billId)
                .customerId("CUST-001")
                .promptTokens(1000)
                .completionTokens(500)
                .totalTokens(1500)
                .includedTokensUsed(1500)
                .overageTokens(0)
                .totalCharge(BigDecimal.ZERO)
                .calculatedAt(now)
                .build();

        when(billMapper.toPO(domainBill)).thenReturn(billPO);
        when(springDataRepository.save(billPO)).thenReturn(billPO);
        when(billMapper.toDomain(billPO)).thenReturn(domainBill);

        Bill result = adapter.save(domainBill);

        assertThat(result).isEqualTo(domainBill);
        verify(billMapper).toPO(domainBill);
        verify(springDataRepository).save(billPO);
        verify(billMapper).toDomain(billPO);
    }

    @Test
    @DisplayName("Should return sum of included tokens when querying for month")
    void should_return_sum_of_included_tokens_when_sum_for_month_given_existing_bills() {
        String customerId = "CUST-001";
        LocalDateTime monthStart = LocalDateTime.of(2026, 3, 1, 0, 0, 0);
        LocalDateTime monthEnd = LocalDateTime.of(2026, 4, 1, 0, 0, 0);
        Integer expectedSum = 50000;

        when(springDataRepository.sumIncludedTokensUsedForMonth(customerId, monthStart, monthEnd))
                .thenReturn(expectedSum);

        Integer result = adapter.sumIncludedTokensUsedForMonth(customerId, monthStart, monthEnd);

        assertThat(result).isEqualTo(expectedSum);
        verify(springDataRepository).sumIncludedTokensUsedForMonth(customerId, monthStart, monthEnd);
    }
}
