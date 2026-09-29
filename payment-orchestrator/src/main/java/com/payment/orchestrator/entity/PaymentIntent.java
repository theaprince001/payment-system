package com.payment.orchestrator.entity;

import com.payment.common.model.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_intents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentIntent {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private UUID payerId;

    @Column(nullable = false)
    private UUID payeeId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false)
    private String paymentMethodId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;   // uses common enum

    private String failureReason;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @Version
    private Long version;

    private String riskDecision;     // ALLOW, REVIEW, BLOCK
    private String riskSource;       // ML, FALLBACK, RULE, ERROR
    private Double riskScore;

    private String reviewedBy;
    private Instant reviewedAt;
    private String reviewNote;

    private String providerOrderId;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
        if (id == null) id = UUID.randomUUID();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}