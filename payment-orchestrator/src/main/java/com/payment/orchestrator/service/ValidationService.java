package com.payment.orchestrator.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.UUID;

@Service
public class ValidationService {
    private final RestTemplate restTemplate = new RestTemplate();

    public boolean isValidUser(UUID userId) {
        // Mock validation - in real system, call user-service
        return true;
    }

    public boolean isValidPaymentMethod(String paymentMethodId, UUID userId) {
        // Mock validation - call payment-method-service
        return true;
    }
}