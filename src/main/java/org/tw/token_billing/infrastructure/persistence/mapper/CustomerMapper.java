package org.tw.token_billing.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import org.tw.token_billing.domain.Customer;
import org.tw.token_billing.infrastructure.persistence.entity.CustomerPO;

@Component
public class CustomerMapper {

    public Customer toDomain(CustomerPO po) {
        if (po == null) {
            return null;
        }
        return Customer.builder()
                .id(po.getId())
                .name(po.getName())
                .createdAt(po.getCreatedAt())
                .build();
    }
}
