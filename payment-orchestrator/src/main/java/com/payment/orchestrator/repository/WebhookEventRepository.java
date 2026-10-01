package com.payment.orchestrator.repository;

import com.payment.orchestrator.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

    boolean existsByEventId(String eventId);
}