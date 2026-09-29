package com.payment.orchestrator.provider;

import com.payment.orchestrator.entity.PaymentIntent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "razorpay")
@Slf4j
public class RazorpayProvider implements PaymentProvider {

    private static final String RAZORPAY_ORDERS_URL = "https://api.razorpay.com/v1/orders";

    private final RestTemplate restTemplate;
    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;

    public RazorpayProvider(
            RestTemplate restTemplate,
            @Value("${razorpay.key.id}") String keyId,
            @Value("${razorpay.key.secret}") String keySecret,
            @Value("${razorpay.webhook.secret}") String webhookSecret) {
        this.restTemplate = restTemplate;
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.webhookSecret = webhookSecret;
        log.info("RazorpayProvider initialized with keyId={}", keyId);
    }

    @Override
    public ProviderResponse initiatePayment(PaymentIntent intent) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBasicAuth(keyId, keySecret);

            // Razorpay expects amount in paise (INR * 100)
            int amountInPaise = intent.getAmount()
                    .multiply(BigDecimal.valueOf(100))
                    .intValue();

            Map<String, Object> body = new HashMap<>();
            body.put("amount", amountInPaise);
            body.put("currency", "INR");

            // Correlation ID — paymentIntentId stored in notes (no length limit)
            Map<String, String> notes = new HashMap<>();
            notes.put("paymentIntentId", intent.getId().toString());
            body.put("notes", notes);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    RAZORPAY_ORDERS_URL, entity, Map.class);

            Map responseBody = response.getBody();
            if (responseBody == null || responseBody.get("id") == null) {
                log.error("Razorpay returned empty or invalid response for intent {}",
                        intent.getId());
                return new ProviderResponse(null, false, true);
            }

            String orderId = responseBody.get("id").toString();
            log.info("Razorpay order created: {} for intent {}", orderId, intent.getId());
            return new ProviderResponse(orderId, true, true);

        } catch (RestClientException e) {
            log.error("Failed to create Razorpay order for intent {}: {}",
                    intent.getId(), e.getMessage());
            return new ProviderResponse(null, false, true);
        } catch (Exception e) {
            log.error("Unexpected error creating Razorpay order for intent {}",
                    intent.getId(), e);
            return new ProviderResponse(null, false, true);
        }
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        throw new UnsupportedOperationException("Session 4");
    }
}