package com.payment.orchestrator.provider;

public record ProviderResponse(
        String providerOrderId,
        boolean success,
        boolean requiresWebhookConfirmation) {}