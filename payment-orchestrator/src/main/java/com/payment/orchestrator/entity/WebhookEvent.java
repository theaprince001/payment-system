package com.payment.orchestrator.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_events",
        uniqueConstraints = @UniqueConstraint(columnNames = "event_id"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String providerOrderId;

    @Column(nullable = false)
    private Instant receivedAt;

    @PrePersist
    protected void onCreate() {
        receivedAt = Instant.now();
        if (id == null) id = UUID.randomUUID();
    }
}