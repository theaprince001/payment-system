package com.payment.orchestrator.controller;

import com.payment.common.dto.CreatePaymentRequest;
import com.payment.common.dto.PaymentResponse;
import com.payment.orchestrator.entity.PaymentIntent;
import com.payment.orchestrator.repository.PaymentIntentRepository;
import com.payment.orchestrator.service.PaymentOrchestratorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentOrchestratorService orchestratorService;
    private final PaymentIntentRepository paymentIntentRepository;

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(@Valid @RequestBody CreatePaymentRequest request) {
        return ResponseEntity.ok(orchestratorService.processPayment(request));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable UUID paymentId) {
        PaymentIntent intent = paymentIntentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
        return ResponseEntity.ok(PaymentResponse.builder()
                .paymentId(intent.getId())
                .status(intent.getStatus())
                .message(intent.getFailureReason())
                .build());
    }
}