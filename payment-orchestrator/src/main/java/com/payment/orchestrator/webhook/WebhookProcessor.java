package com.payment.orchestrator.webhook;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Handler for a specific payment provider's webhook events.
 * Adding a new provider means adding a new implementation of this interface
 * the controller and dispatcher do not change.
 */
public interface WebhookProcessor {

    /** Provider identifier as used in the URL path, e.g. "razorpay". */
    String provider();

    /** Verifies the provider-specific signature against the raw request body. */
    boolean verifySignature(String rawBody, String signature);

    /**
     * Extracts the deduplication event ID from a parsed payload.
     * Must be stable across redeliveries of the same logical event.
     */
    String extractEventId(JsonNode payload);

    /** Extracts the provider-side order ID that identifies our payment intent. */
    String extractProviderOrderId(JsonNode payload);

    /** Extracts the event type, normalized if needed. */
    String extractEventType(JsonNode payload);

    /** Processes a verified, deduplicated event. */
    void processEvent(String eventId, String eventType, String providerOrderId, JsonNode payload);
}