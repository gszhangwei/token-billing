package org.tw.token_billing.infrastructure.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.tw.token_billing.domain.Customer;
import org.tw.token_billing.infrastructure.persistence.mapper.CustomerMapper;
import org.tw.token_billing.repository.CustomerRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JpaCustomerRepositoryAdapter implements CustomerRepository {

    private final SpringDataCustomerRepository springDataRepository;
    private final CustomerMapper customerMapper;

    @Override
    public Optional<Customer> findById(String id) {
        return springDataRepository.findById(id)
                .map(customerMapper::toDomain);
    }
}
