package com.payment.orchestrator.provider;

import com.payment.orchestrator.entity.PaymentIntent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "razorpay")
public class RazorpayProvider implements PaymentProvider {

    @Override
    public ProviderResponse initiatePayment(PaymentIntent intent) {
        throw new UnsupportedOperationException("Not implemented yet — Session 2");
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        throw new UnsupportedOperationException("Not implemented yet — Session 4");
    }
}