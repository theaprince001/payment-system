package com.payment.orchestrator.service;

import com.payment.common.dto.CreatePaymentRequest;
import com.payment.common.dto.LedgerEntryDto;
import com.payment.common.dto.PaymentResponse;
import com.payment.common.model.PaymentStatus;
import com.payment.orchestrator.entity.PaymentIntent;
import com.payment.orchestrator.messaging.PaymentEventPublisher;
import com.payment.orchestrator.repository.PaymentIntentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
        if (!validationService.isValidPaymentMethod(request.getPaymentMethodId(), request.getPayerId())) {
            return createFailedResponse(request.getIdempotencyKey(), "Invalid payment method");
        }

        // Create payment intent
        PaymentIntent intent = PaymentIntent.builder()
                .idempotencyKey(request.getIdempotencyKey())
                .payerId(request.getPayerId())
                .payeeId(request.getPayeeId())
                .amount(request.getAmount())
                .paymentMethodId(request.getPaymentMethodId())
                .status(PaymentStatus.CREATED)
                .build();
        paymentIntentRepository.save(intent);

        intent.setStatus(PaymentStatus.PENDING);
        paymentIntentRepository.save(intent);

        // Publish ledger events
        LedgerEntryDto debitEntry = LedgerEntryDto.builder()
                .accountId(request.getPayerId())
                .amount(request.getAmount().negate())
                .paymentId(intent.getId())
                .timestamp(Instant.now())
                .build();
        eventPublisher.publishLedgerDebit(debitEntry);

        LedgerEntryDto creditEntry = LedgerEntryDto.builder()
                .accountId(request.getPayeeId())
                .amount(request.getAmount())
                .paymentId(intent.getId())
                .timestamp(Instant.now())
                .build();
        eventPublisher.publishLedgerCredit(creditEntry);

        PaymentResponse response = PaymentResponse.builder()
                .paymentId(intent.getId())
                .status(PaymentStatus.PENDING)
                .message("Payment processing initiated")
                .build();
        idempotencyService.save(request.getIdempotencyKey(), response);
        return response;
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