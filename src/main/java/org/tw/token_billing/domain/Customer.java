package org.tw.token_billing.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class Customer {
    private final String id;
    private final String name;
    private final LocalDateTime createdAt;
}
