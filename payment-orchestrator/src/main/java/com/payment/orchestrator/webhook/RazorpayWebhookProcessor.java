package com.payment.orchestrator.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.payment.common.dto.LedgerEntryDto;
import com.payment.common.model.PaymentStatus;
import com.payment.orchestrator.entity.PaymentIntent;
import com.payment.orchestrator.entity.WebhookEvent;
import com.payment.orchestrator.messaging.PaymentEventPublisher;
import com.payment.orchestrator.repository.PaymentIntentRepository;
import com.payment.orchestrator.repository.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class RazorpayWebhookProcessor {

    private final WebhookEventRepository webhookEventRepository;
    private final PaymentIntentRepository paymentIntentRepository;
    private final PaymentEventPublisher eventPublisher;

    @Value("${razorpay.webhook.secret}")
    private String webhookSecret;

    /**
     * Verifies HMAC-SHA256 of the raw body using the configured webhook secret.
     * Uses constant-time comparison to prevent timing attacks.
     */
    public boolean verifySignature(String rawBody, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            byte[] received = HexFormat.of().parseHex(signature);
            return MessageDigest.isEqual(computed, received);
        } catch (Exception e) {
            log.error("Signature verification error", e);
            return false;
        }
    }

    @Transactional
    public void processEvent(String eventId, String eventType, String providerOrderId, JsonNode payload) {

        // Layer 1 — quick existence check (Redis-style fast path)
        if (webhookEventRepository.existsByEventId(eventId)) {
            log.info("Webhook event {} already processed — ignoring duplicate", eventId);
            return;
        }

        // Layer 2 — durable dedup. The unique constraint on event_id is the real safety net.
        // If two threads race, the second saveAndFlush throws, and we skip.
        try {
            WebhookEvent event = WebhookEvent.builder()
                    .eventId(eventId)
                    .eventType(eventType)
                    .providerOrderId(providerOrderId == null ? "unknown" : providerOrderId)
                    .build();
            webhookEventRepository.saveAndFlush(event);
        } catch (Exception e) {
            log.info("Concurrent webhook event {} detected — skipping", eventId);
            return;
        }

        // Handle based on event type
        switch (eventType) {
            case "payment.captured", "order.paid" -> handleCaptured(providerOrderId);
            case "payment.failed" -> handleFailed(providerOrderId, payload);
            default -> log.debug("Unhandled webhook event type: {}", eventType);
        }
    }

    private void handleCaptured(String providerOrderId) {
        PaymentIntent intent = paymentIntentRepository.findByProviderOrderId(providerOrderId)
                .orElseThrow(() -> new RuntimeException("No intent for order " + providerOrderId));

        // Conditional update — only AWAITING_PROVIDER transitions to PENDING
        int updated = paymentIntentRepository.updateStatusConditionally(
                intent.getId(),
                PaymentStatus.AWAITING_PROVIDER,
                PaymentStatus.PENDING,
                null,
                "system",
                Instant.now());

        if (updated == 0) {
            log.info("Payment {} not in AWAITING_PROVIDER — captured event ignored", intent.getId());
            return;
        }

        // Publish ledger events — same shape as the mock flow
        LedgerEntryDto debit = LedgerEntryDto.builder()
                .accountId(intent.getPayerId())
                .amount(intent.getAmount().negate())
                .paymentId(intent.getId())
                .timestamp(Instant.now())
                .build();
        eventPublisher.publishLedgerDebit(debit);

        LedgerEntryDto credit = LedgerEntryDto.builder()
                .accountId(intent.getPayeeId())
                .amount(intent.getAmount())
                .paymentId(intent.getId())
                .timestamp(Instant.now())
                .build();
        eventPublisher.publishLedgerCredit(credit);

        log.info("Payment {} captured — ledger events published", intent.getId());
    }

    private void handleFailed(String providerOrderId, JsonNode payload) {
        PaymentIntent intent = paymentIntentRepository.findByProviderOrderId(providerOrderId)
                .orElseThrow(() -> new RuntimeException("No intent for order " + providerOrderId));

        String reason = payload.path("payload").path("payment").path("entity")
                .path("error_description").asText("Payment failed at provider");

        int updated = paymentIntentRepository.updateStatusConditionally(
                intent.getId(),
                PaymentStatus.AWAITING_PROVIDER,
                PaymentStatus.FAILED,
                reason,
                "system",
                Instant.now());

        if (updated == 0) {
            log.info("Payment {} not in AWAITING_PROVIDER — failed event ignored", intent.getId());
        } else {
            log.warn("Payment {} marked FAILED: {}", intent.getId(), reason);
        }
    }
}