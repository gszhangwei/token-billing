package org.tw.token_billing.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @Column(length = 50)
    private String id;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Customer() {}

    public Customer(String id, String name, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}