package com.payment.orchestrator.messaging;

import com.payment.orchestrator.service.PaymentOrchestratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class LedgerEventConsumer {
    private final PaymentOrchestratorService orchestratorService;

    @RabbitListener(queues = "ledger.success.queue")
    public void onLedgerSuccess(UUID paymentId) {
        log.info("Received ledger success for payment: {}", paymentId);
        orchestratorService.handleLedgerSuccess(paymentId);
    }

    @RabbitListener(queues = "ledger.failure.queue")
    public void onLedgerFailure(LedgerFailureEvent event) {
        log.info("Received ledger failure for payment: {}, reason: {}",
                event.paymentId(), event.reason());
        orchestratorService.handleLedgerFailure(event.paymentId(), event.reason());
    }

    public record LedgerFailureEvent(UUID paymentId, String reason) {}
}