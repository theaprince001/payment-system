package com.payment.ledger.controller;

import com.payment.ledger.entity.LedgerEntry;
import com.payment.ledger.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/ledger")
@RequiredArgsConstructor
public class TopUpController {

    private final LedgerEntryRepository ledgerEntryRepository;

    @PostMapping("/topup")
    public ResponseEntity<?> topUp(@RequestParam UUID userId,
                                   @RequestParam BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body("Amount must be positive");
        }

        LedgerEntry entry = LedgerEntry.builder()
                .accountId(userId)
                .amount(amount)
                .paymentId(UUID.randomUUID())   // dummy reference for top-up
                .timestamp(Instant.now())
                .build();

        ledgerEntryRepository.save(entry);
        return ResponseEntity.ok("Top-up successful");
    }
}