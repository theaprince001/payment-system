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
public class ProviderTimeoutScheduler {

    private final PaymentIntentRepository paymentIntentRepository;

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void expireStaleProviderPayments() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.MINUTES);
        List<PaymentIntent> stale = paymentIntentRepository
                .findByStatusAndCreatedAtBefore(PaymentStatus.AWAITING_PROVIDER, cutoff);

        for (PaymentIntent intent : stale) {
            // Conditional update — same pattern as the review scheduler.
            int updated = paymentIntentRepository.updateStatusConditionally(
                    intent.getId(),
                    PaymentStatus.AWAITING_PROVIDER,
                    PaymentStatus.EXPIRED,
                    "No webhook received within 30 minutes",
                    "system",
                    Instant.now()
            );
            if (updated == 0) {
                log.info("Skipping {} — status already changed by another process", intent.getId());
            } else {
                log.warn("Expired stuck payment {} after 30 min in AWAITING_PROVIDER", intent.getId());
            }
        }
    }
}