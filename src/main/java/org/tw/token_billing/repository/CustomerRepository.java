package org.tw.token_billing.repository;

import java.util.Optional;

import org.tw.token_billing.domain.Customer;

public interface CustomerRepository {
    Optional<Customer> findById(String id);
}
