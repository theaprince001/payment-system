package com.payment.orchestrator.scheduler;

import com.payment.common.model.PaymentStatus;
import com.payment.orchestrator.entity.PaymentIntent;
import com.payment.orchestrator.repository.PaymentIntentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewTimeoutScheduler {

    private final PaymentIntentRepository paymentIntentRepository;

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void autoRejectExpiredReviews() {
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        List<PaymentIntent> expired = paymentIntentRepository
                .findByStatusAndCreatedAtBefore(PaymentStatus.PENDING_REVIEW, cutoff);

        for (PaymentIntent intent : expired) {
            int updated = paymentIntentRepository.updateStatusConditionally(
                    intent.getId(),
                    PaymentStatus.PENDING_REVIEW,
                    PaymentStatus.FAILED,
                    "Review timeout – auto‑rejected",
                    "system",
                    Instant.now()
            );
            if (updated == 0) {
                log.info("Skipping payment {} because it was already actioned", intent.getId());
            }
        }
    }
}