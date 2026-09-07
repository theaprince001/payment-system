package com.payment.ledger.controller;


import com.payment.ledger.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/ledger")
@RequiredArgsConstructor
public class BalanceController {

    private final LedgerEntryRepository ledgerEntryRepository;

    @GetMapping("/balance")
    public ResponseEntity<BigDecimal> getBalance(@RequestParam UUID userId){
        BigDecimal balance = ledgerEntryRepository.sumAmountByAccountId(userId)
                .orElse(BigDecimal.ZERO);
        return ResponseEntity.ok(balance);
    }
}
