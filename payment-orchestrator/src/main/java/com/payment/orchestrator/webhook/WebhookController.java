package com.payment.orchestrator.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.orchestrator.webhook.RazorpayWebhookProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final RazorpayWebhookProcessor webhookProcessor;
    private final ObjectMapper objectMapper;

    @PostMapping("/razorpay")
    public ResponseEntity<String> handleRazorpayWebhook(
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestBody String rawBody) {

        if (signature == null || signature.isBlank()) {
            log.warn("Razorpay webhook received without signature header");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing signature");
        }

        try {
            boolean valid = webhookProcessor.verifySignature(rawBody, signature);
            if (!valid) {
                log.warn("Razorpay webhook signature verification failed");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
            }

            JsonNode root = objectMapper.readTree(rawBody);

            String eventType = root.path("event").asText();

            // Razorpay payment entity ID — unique per payment, good idempotency key
            String eventId = root.path("payload").path("payment").path("entity")
                    .path("id").asText(null);

            // Fallback for events that don't carry a payment entity (e.g. order.paid)
            if (eventId == null || eventId.isBlank()) {
                String orderId = root.path("payload").path("order").path("entity")
                        .path("id").asText("");
                eventId = "fallback_" + orderId + "_" + eventType;
            }

            // The provider order id we stored when we created the order
            String providerOrderId = root.path("payload").path("payment").path("entity")
                    .path("order_id").asText(null);

            if (providerOrderId == null || providerOrderId.isBlank()) {
                providerOrderId = root.path("payload").path("order").path("entity")
                        .path("id").asText(null);
            }

            webhookProcessor.processEvent(eventId, eventType, providerOrderId, root);

            return ResponseEntity.ok("ok");

        } catch (Exception e) {
            log.error("Error processing Razorpay webhook", e);
            // Return 200 so Razorpay stops retrying — a broken body or internal bug
            // won't fix itself on retry. The event is logged for manual investigation.
            return ResponseEntity.ok("received-with-error");
        }
    }
}