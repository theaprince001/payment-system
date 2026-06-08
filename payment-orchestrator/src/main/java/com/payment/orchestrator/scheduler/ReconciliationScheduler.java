package com.payment.orchestrator.scheduler;

import com.payment.common.model.PaymentStatus;
import com.payment.orchestrator.entity.PaymentIntent;
import com.payment.orchestrator.messaging.PaymentEventPublisher;
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
public class ReconciliationScheduler {
    private final PaymentIntentRepository paymentIntentRepository;
    private final PaymentEventPublisher eventPublisher;

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void reconcileStuckPayments() {
        Instant fiveMinutesAgo = Instant.now().minus(5, ChronoUnit.MINUTES);
        List<PaymentIntent> stuckIntents = paymentIntentRepository
                .findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, fiveMinutesAgo);

        for (PaymentIntent intent : stuckIntents) {
            log.warn("Found stuck payment {}, initiating reversal", intent.getId());
            eventPublisher.publishReversal(intent.getId(), intent.getPayerId(), intent.getAmount());
            intent.setStatus(PaymentStatus.FAILED);
            intent.setFailureReason("Reconciled: stuck in PENDING");
            paymentIntentRepository.save(intent);
        }
    }
}