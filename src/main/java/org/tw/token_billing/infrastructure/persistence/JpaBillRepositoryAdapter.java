package org.tw.token_billing.infrastructure.persistence;

import java.time.LocalDateTime;

import org.springframework.stereotype.Repository;
import org.tw.token_billing.domain.Bill;
import org.tw.token_billing.infrastructure.persistence.entity.BillPO;
import org.tw.token_billing.infrastructure.persistence.mapper.BillMapper;
import org.tw.token_billing.repository.BillRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JpaBillRepositoryAdapter implements BillRepository {

    private final SpringDataBillRepository springDataRepository;
    private final BillMapper billMapper;

    @Override
    public Bill save(Bill bill) {
        BillPO po = billMapper.toPO(bill);
        BillPO savedPO = springDataRepository.save(po);
        return billMapper.toDomain(savedPO);
    }

    @Override
    public Integer sumIncludedTokensUsedForMonth(String customerId, LocalDateTime monthStart, LocalDateTime monthEnd) {
        return springDataRepository.sumIncludedTokensUsedForMonth(customerId, monthStart, monthEnd);
    }
}
