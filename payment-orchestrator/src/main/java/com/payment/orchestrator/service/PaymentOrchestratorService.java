package com.payment.orchestrator.service;

import com.payment.common.dto.*;
import com.payment.common.model.PaymentStatus;
import com.payment.orchestrator.client.FraudServiceClient;
import com.payment.orchestrator.entity.PaymentIntent;
import com.payment.orchestrator.exception.FraudServiceInternalException;
import com.payment.orchestrator.messaging.PaymentEventPublisher;
import com.payment.orchestrator.provider.PaymentProvider;
import com.payment.orchestrator.provider.ProviderResponse;
import com.payment.orchestrator.repository.PaymentIntentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentOrchestratorService {

    private final PaymentIntentRepository paymentIntentRepository;
    private final IdempotencyService idempotencyService;
    private final RateLimitService rateLimitService;
    private final ValidationService validationService;
    private final PaymentEventPublisher eventPublisher;
    private final RestTemplate restTemplate;
    private final FraudServiceClient fraudServiceClient;
    private final PaymentProvider paymentProvider;

    @Value("${PAYMENT_METHOD_SERVICE_URL:http://localhost:8083}")
    private String paymentMethodServiceUrl;

    @Transactional
    public PaymentResponse processPayment(CreatePaymentRequest request) {
        // Idempotency check
        var cachedResponse = idempotencyService.get(request.getIdempotencyKey());
        if (cachedResponse.isPresent()) {
            return cachedResponse.get();
        }

        var existingIntent = paymentIntentRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existingIntent.isPresent()) {
            PaymentIntent intent = existingIntent.get();
            PaymentResponse response = mapToResponse(intent);
            idempotencyService.save(request.getIdempotencyKey(), response);
            return response;
        }

        // Rate limit
        if (!rateLimitService.isAllowed(request.getPayerId(), "payment")) {
            PaymentResponse response = PaymentResponse.builder()
                    .status(PaymentStatus.FAILED)
                    .message("Rate limit exceeded")
                    .build();
            idempotencyService.save(request.getIdempotencyKey(), response);
            return response;
        }

        // Validation
        if (!validationService.isValidUser(request.getPayerId()) ||
                !validationService.isValidUser(request.getPayeeId())) {
            return createFailedResponse(request.getIdempotencyKey(), "Invalid user");
        }

        // Resolve payment method
        String paymentMethodId = request.getPaymentMethodId();
        if (paymentMethodId == null || paymentMethodId.isEmpty()) {
            try {
                paymentMethodId = createPaymentMethod(request);
            } catch (Exception e) {
                log.error("Failed to create payment method: {}", e.getMessage());
                return createFailedResponse(request.getIdempotencyKey(), "Payment method creation failed");
            }
        }

        if (!validationService.isValidPaymentMethod(paymentMethodId, request.getPayerId())) {
            return createFailedResponse(request.getIdempotencyKey(), "Invalid payment method");
        }

        // ============== FRAUD CHECK (PRE-AUTHORIZATION) ==============
        RiskAssessment risk = assessRisk(request);
        PaymentIntent intent = PaymentIntent.builder()
                .idempotencyKey(request.getIdempotencyKey())
                .payerId(request.getPayerId())
                .payeeId(request.getPayeeId())
                .amount(request.getAmount())
                .paymentMethodId(paymentMethodId)
                .riskDecision(risk.decision())
                .riskSource(risk.source())
                .riskScore(risk.score())
                .status(PaymentStatus.CREATED)
                .build();
        paymentIntentRepository.save(intent);

        if (RiskAssessment.DECISION_SYSTEM_ERROR.equals(risk.decision())) {
            intent.setStatus(PaymentStatus.FAILED);
            intent.setFailureReason("Temporarily unavailable, please try again");
            paymentIntentRepository.save(intent);
            PaymentResponse response = PaymentResponse.builder()
                    .paymentId(intent.getId())
                    .status(PaymentStatus.FAILED)
                    .message("Payment service temporarily unavailable, please retry")
                    .build();
            idempotencyService.save(request.getIdempotencyKey(), response);
            return response;
        }

        if (RiskAssessment.DECISION_BLOCK.equals(risk.decision())) {
            intent.setStatus(PaymentStatus.FAILED);
            intent.setFailureReason("Payment blocked by risk assessment");
            paymentIntentRepository.save(intent);
            PaymentResponse response = PaymentResponse.builder()
                    .paymentId(intent.getId())
                    .status(PaymentStatus.FAILED)
                    .message("Payment blocked by risk assessment")
                    .build();
            idempotencyService.save(request.getIdempotencyKey(), response);
            return response;
        }

        if (RiskAssessment.DECISION_REVIEW.equals(risk.decision())) {
            intent.setStatus(PaymentStatus.PENDING_REVIEW);
            paymentIntentRepository.save(intent);
            PaymentResponse response = PaymentResponse.builder()
                    .paymentId(intent.getId())
                    .status(PaymentStatus.PENDING_REVIEW)
                    .message("Payment flagged for manual review")
                    .build();
            idempotencyService.save(request.getIdempotencyKey(), response);
            return response;
        }

        // ============== ALLOW: proceed to provider ==============
        // Only call the provider when we've decided to proceed. This prevents
        // orphaned orders on the provider's side for payments we've blocked.
        // ============== ALLOW: proceed to provider ==============
        ProviderResponse providerResponse = paymentProvider.initiatePayment(intent);
        if (!providerResponse.success()) {
            intent.setStatus(PaymentStatus.FAILED);
            intent.setFailureReason("Provider rejected the payment");
            paymentIntentRepository.save(intent);
            return createFailedResponse(request.getIdempotencyKey(), "Provider rejected the payment");
        }

        intent.setProviderOrderId(providerResponse.providerOrderId());

        if (providerResponse.requiresWebhookConfirmation()) {
            intent.setStatus(PaymentStatus.AWAITING_PROVIDER);
            intent.setProviderOrderId(providerResponse.providerOrderId());
            paymentIntentRepository.save(intent);
            PaymentResponse response = PaymentResponse.builder()
                    .paymentId(intent.getId())
                    .status(PaymentStatus.AWAITING_PROVIDER)
                    .message("Payment initiated with provider; awaiting confirmation")
                    .providerOrderId(providerResponse.providerOrderId())   // ← new
                    .build();
            idempotencyService.save(request.getIdempotencyKey(), response);
            return response;
        }

// Mock path: settlement is synchronous.
        intent.setStatus(PaymentStatus.PENDING);
        paymentIntentRepository.save(intent);

        publishLedgerEvents(request, intent.getId());

        PaymentResponse response = PaymentResponse.builder()
                .paymentId(intent.getId())
                .status(PaymentStatus.PENDING)
                .message("Payment processing initiated")
                .build();
        idempotencyService.save(request.getIdempotencyKey(), response);
        return response;

    }

    private RiskAssessment assessRisk(CreatePaymentRequest request) {
        RiskRequest riskRequest = buildRiskRequest(request);
        try {
            RiskResponse response = fraudServiceClient.callFraudService(riskRequest);
            if (response == null) {
                return new RiskAssessment(0.0, RiskAssessment.DECISION_SYSTEM_ERROR, "ERROR");
            }
            return new RiskAssessment(
                    response.getScore(),
                    response.getDecision(),
                    response.isFallback() ? "FALLBACK" : "ML"
            );
        } catch (FraudServiceInternalException e) {
            log.error("Unexpected internal error in fraud service", e);
            // In production, send to Sentry here.
            return new RiskAssessment(0.0, RiskAssessment.DECISION_SYSTEM_ERROR, "ERROR");
        }
    }

    private RiskRequest buildRiskRequest(CreatePaymentRequest request) {
        RiskRequest riskRequest = new RiskRequest();
        riskRequest.setPayerId(request.getPayerId().toString());
        riskRequest.setPayeeId(request.getPayeeId().toString());
        riskRequest.setAmount(request.getAmount());
        riskRequest.setPaymentMethodType(request.getPaymentMethodType());
        riskRequest.setPaymentMethodIdentifier(request.getPaymentMethodIdentifier());
        // Mock values; later replace with real data
        riskRequest.setUserAccountAgeDays(30);
        riskRequest.setPaymentMethodAgeSeconds(60);
        riskRequest.setTransactionVelocity(2);
        return riskRequest;
    }

    private void publishLedgerEvents(CreatePaymentRequest request, UUID paymentId) {
        LedgerEntryDto debitEntry = LedgerEntryDto.builder()
                .accountId(request.getPayerId())
                .amount(request.getAmount().negate())
                .paymentId(paymentId)
                .timestamp(Instant.now())
                .build();
        eventPublisher.publishLedgerDebit(debitEntry);

        LedgerEntryDto creditEntry = LedgerEntryDto.builder()
                .accountId(request.getPayeeId())
                .amount(request.getAmount())
                .paymentId(paymentId)
                .timestamp(Instant.now())
                .build();
        eventPublisher.publishLedgerCredit(creditEntry);
    }

    private String createPaymentMethod(CreatePaymentRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", request.getPayerId().toString());
        headers.set("Content-Type", "application/json");

        AddPaymentMethodRequest methodRequest = new AddPaymentMethodRequest();
        methodRequest.setType(request.getPaymentMethodType());
        methodRequest.setIdentifier(request.getPaymentMethodIdentifier());

        HttpEntity<AddPaymentMethodRequest> entity = new HttpEntity<>(methodRequest, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                paymentMethodServiceUrl + "/api/payment-methods",
                entity,
                Map.class
        );

        if (response.getBody() != null && response.getBody().get("id") != null) {
            return response.getBody().get("id").toString();
        }
        throw new RuntimeException("Payment method service returned no ID");
    }

    private PaymentResponse createFailedResponse(String idempotencyKey, String reason) {
        PaymentResponse response = PaymentResponse.builder()
                .status(PaymentStatus.FAILED)
                .message(reason)
                .build();
        idempotencyService.save(idempotencyKey, response);
        return response;
    }

    private PaymentResponse mapToResponse(PaymentIntent intent) {
        return PaymentResponse.builder()
                .paymentId(intent.getId())
                .status(intent.getStatus())
                .message(intent.getFailureReason())
                .build();
    }

    @Transactional
    public void handleLedgerSuccess(UUID paymentId) {
        PaymentIntent intent = paymentIntentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment intent not found"));
        if (intent.getStatus() == PaymentStatus.PENDING) {
            intent.setStatus(PaymentStatus.SUCCESS);
            paymentIntentRepository.save(intent);
            eventPublisher.publishPaymentCompleted(paymentId);
        }
    }

    @Transactional
    public void handleLedgerFailure(UUID paymentId, String reason) {
        PaymentIntent intent = paymentIntentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment intent not found"));
        intent.setStatus(PaymentStatus.FAILED);
        intent.setFailureReason(reason);
        paymentIntentRepository.save(intent);
        eventPublisher.publishPaymentFailed(paymentId, reason);
    }
}