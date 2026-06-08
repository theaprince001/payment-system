package com.payment.paymentmethod.controller;

import com.payment.paymentmethod.dto.AddPaymentMethodRequest;
import com.payment.paymentmethod.entity.PaymentMethod;
import com.payment.paymentmethod.repository.PaymentMethodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payment-methods")
@RequiredArgsConstructor
public class PaymentMethodController {
    private final PaymentMethodRepository repository;

    @PostMapping
    public ResponseEntity<PaymentMethod> addPaymentMethod(@RequestHeader("X-User-Id") UUID userId,
                                                          @RequestBody AddPaymentMethodRequest request) {
        PaymentMethod pm = PaymentMethod.builder()
                .userId(userId)
                .type(request.getType())
                .maskedIdentifier("****" + request.getIdentifier().substring(Math.max(0, request.getIdentifier().length() - 4)))
                .token("mock_token_" + UUID.randomUUID())
                .verified(false)
                .build();
        return ResponseEntity.ok(repository.save(pm));
    }

    @GetMapping
    public ResponseEntity<List<PaymentMethod>> getUserPaymentMethods(@RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(repository.findByUserId(userId));
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<Void> verifyPaymentMethod(@PathVariable UUID id) {
        PaymentMethod pm = repository.findById(id).orElseThrow();
        pm.setVerified(true);
        repository.save(pm);
        return ResponseEntity.ok().build();
    }
}