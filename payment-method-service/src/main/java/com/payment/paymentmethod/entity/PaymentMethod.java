package com.payment.paymentmethod.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_methods")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMethod {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    private PaymentMethodType type;

    private String maskedIdentifier;

    @Column(nullable = false)
    private String token;

    private boolean verified;
    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
        if (id == null) id = UUID.randomUUID();
    }

    public enum PaymentMethodType {
        CARD, UPI, BANK
    }
}