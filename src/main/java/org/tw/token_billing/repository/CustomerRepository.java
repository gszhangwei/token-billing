package org.tw.token_billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tw.token_billing.entity.Customer;

public interface CustomerRepository extends JpaRepository<Customer, String> {
}
