package com.payment.orchestrator.provider;

import com.payment.orchestrator.entity.PaymentIntent;

public interface PaymentProvider {
    ProviderResponse initiatePayment(PaymentIntent intent);
    boolean verifyWebhookSignature(String rawBody, String signature);
}