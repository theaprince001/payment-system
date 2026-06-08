package com.payment.orchestrator.messaging;

import com.payment.common.dto.LedgerEntryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PaymentEventPublisher {
    private final RabbitTemplate rabbitTemplate;

    public void publishLedgerDebit(LedgerEntryDto debitEntry) {
        rabbitTemplate.convertAndSend("payment.exchange", "ledger.debit", debitEntry);
    }

    public void publishLedgerCredit(LedgerEntryDto creditEntry) {
        rabbitTemplate.convertAndSend("payment.exchange", "ledger.credit", creditEntry);
    }

    public void publishPaymentCompleted(UUID paymentId) {
        rabbitTemplate.convertAndSend("payment.exchange", "payment.completed", paymentId);
    }

    public void publishPaymentFailed(UUID paymentId, String reason) {
        rabbitTemplate.convertAndSend("payment.exchange", "payment.failed",
                new PaymentFailedEvent(paymentId, reason));
    }

    public void publishReversal(UUID paymentId, UUID accountId, BigDecimal amount) {
        LedgerEntryDto reversal = LedgerEntryDto.builder()
                .accountId(accountId)
                .amount(amount) // positive (credit back)
                .paymentId(paymentId)
                .timestamp(Instant.now())
                .build();
        rabbitTemplate.convertAndSend("payment.exchange", "ledger.credit", reversal);
    }

    public record PaymentFailedEvent(UUID paymentId, String reason) {}
}