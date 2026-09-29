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
        try {
            log.info("Received ledger success for payment: {}", paymentId);
            orchestratorService.handleLedgerSuccess(paymentId);
        } catch (Exception e) {
            // Ack and drop — a deterministic error on this message will never
            // succeed on retry. Rethrowing would cause an infinite redelivery loop.
            log.error("Failed to process ledger success for payment {}. "
                    + "Acking and dropping to prevent redelivery loop.", paymentId, e);
        }
    }

    @RabbitListener(queues = "ledger.failure.queue")
    public void onLedgerFailure(LedgerFailureEvent event) {
        try {
            log.info("Received ledger failure for payment: {}, reason: {}",
                    event.paymentId(), event.reason());
            orchestratorService.handleLedgerFailure(event.paymentId(), event.reason());
        } catch (Exception e) {
            // Same reasoning as above — never rethrow from a consumer, or RabbitMQ
            // will requeue the message indefinitely and starve healthy traffic.
            log.error("Failed to process ledger failure for payment {}. "
                            + "Acking and dropping to prevent redelivery loop.",
                    event.paymentId(), e);
        }
    }

    public record LedgerFailureEvent(UUID paymentId, String reason) {}
}