package com.payment.orchestrator.controller;

import com.payment.common.model.PaymentStatus;
import com.payment.orchestrator.entity.PaymentIntent;
import com.payment.orchestrator.repository.PaymentIntentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/review")
@RequiredArgsConstructor
public class ReviewController {

    private final PaymentIntentRepository paymentIntentRepository;

    @PostMapping("/{paymentId}")
    public ResponseEntity<?> review(@PathVariable UUID paymentId,
                                    @RequestParam String action,
                                    @RequestParam String reviewedBy,
                                    @RequestParam String note) {
        PaymentIntent intent = paymentIntentRepository.findById(paymentId).orElseThrow();

        if (intent.getStatus() != PaymentStatus.PENDING_REVIEW) {
            return ResponseEntity.badRequest().body("Payment is not in review state");
        }

        if ("APPROVE".equalsIgnoreCase(action)) {
            intent.setStatus(PaymentStatus.PENDING);
            intent.setReviewedBy(reviewedBy);
            intent.setReviewedAt(Instant.now());
            intent.setReviewNote(note);
            paymentIntentRepository.save(intent);
            // In a full implementation, publish ledger events here
        } else if ("REJECT".equalsIgnoreCase(action)) {
            intent.setStatus(PaymentStatus.FAILED);
            intent.setReviewedBy(reviewedBy);
            intent.setReviewedAt(Instant.now());
            intent.setReviewNote(note);
            intent.setFailureReason("Rejected by " + reviewedBy + ": " + note);
            paymentIntentRepository.save(intent);
        } else {
            return ResponseEntity.badRequest().body("Invalid action");
        }
        return ResponseEntity.ok("Review processed");
    }
}