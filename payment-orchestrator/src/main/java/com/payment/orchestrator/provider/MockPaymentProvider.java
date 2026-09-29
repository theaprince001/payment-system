package com.payment.orchestrator.provider;

import com.payment.orchestrator.entity.PaymentIntent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "mock", matchIfMissing = true)
@Slf4j
public class MockPaymentProvider implements PaymentProvider {

    @Override
    public ProviderResponse initiatePayment(PaymentIntent intent) {
        String mockOrderId = "mock_order_" + UUID.randomUUID();
        log.info("Mock provider created order {} for intent {}", mockOrderId, intent.getId());
        return new ProviderResponse(mockOrderId, true, false);   // no webhook needed
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        return true;
    }
}