package com.payment.ledger.service;

import com.payment.common.dto.LedgerEntryDto;
import com.payment.ledger.entity.LedgerEntry;
import com.payment.ledger.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerService {
    private final LedgerEntryRepository ledgerEntryRepository;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = "ledger.debit.queue")
    @Transactional
    public void processDebit(LedgerEntryDto debitDto) {
        log.info("Processing debit for payment: {}", debitDto.getPaymentId());
        try {
            BigDecimal currentBalance = getCurrentBalance(debitDto.getAccountId());
            if (currentBalance.add(debitDto.getAmount()).compareTo(BigDecimal.ZERO) < 0) {
                throw new InsufficientBalanceException("Insufficient balance");
            }
            LedgerEntry entry = mapToEntity(debitDto);
            ledgerEntryRepository.save(entry);
            rabbitTemplate.convertAndSend("payment.exchange", "ledger.success", debitDto.getPaymentId());
        } catch (Exception e) {
            log.error("Debit failed: {}", e.getMessage());
            rabbitTemplate.convertAndSend("payment.exchange", "ledger.failure",
                    new LedgerFailureEvent(debitDto.getPaymentId(), e.getMessage()));
            throw e;
        }
    }

    @RabbitListener(queues = "ledger.credit.queue")
    @Transactional
    public void processCredit(LedgerEntryDto creditDto) {
        log.info("Processing credit for payment: {}", creditDto.getPaymentId());
        try {
            LedgerEntry entry = mapToEntity(creditDto);
            ledgerEntryRepository.save(entry);
        } catch (Exception e) {
            log.error("Credit failed: {}", e.getMessage());
            rabbitTemplate.convertAndSend("payment.exchange", "ledger.failure",
                    new LedgerFailureEvent(creditDto.getPaymentId(), e.getMessage()));
            throw e;
        }
    }

    private BigDecimal getCurrentBalance(UUID accountId) {
        return ledgerEntryRepository.sumAmountByAccountId(accountId).orElse(BigDecimal.ZERO);
    }

    private LedgerEntry mapToEntity(LedgerEntryDto dto) {
        return LedgerEntry.builder()
                .accountId(dto.getAccountId())
                .amount(dto.getAmount())
                .paymentId(dto.getPaymentId())
                .timestamp(dto.getTimestamp())
                .build();
    }

    public static class InsufficientBalanceException extends RuntimeException {
        public InsufficientBalanceException(String message) { super(message); }
    }

    public record LedgerFailureEvent(UUID paymentId, String reason) {}
}