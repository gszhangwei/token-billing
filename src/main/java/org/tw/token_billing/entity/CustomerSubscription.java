package org.tw.token_billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "customer_subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class CustomerSubscription {

    @Id
    private UUID id;

    @Column(name = "customer_id")
    private String customerId;

    @Column(name = "plan_id")
    private String planId;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "created_at")
    private Instant createdAt;
}
