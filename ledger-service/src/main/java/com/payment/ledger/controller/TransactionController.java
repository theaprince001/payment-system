package com.payment.ledger.controller;

import com.payment.ledger.entity.LedgerEntry;
import com.payment.ledger.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ledger")
@RequiredArgsConstructor
public class TransactionController {

    private final LedgerEntryRepository ledgerEntryRepository;

    @GetMapping("/transactions")
    public ResponseEntity<List<LedgerEntry>> getTransactions(@RequestParam UUID userId){
        List<LedgerEntry> transactions = ledgerEntryRepository
                .findByAccountIdOrderByTimestampDesc(userId);
        return ResponseEntity.ok(transactions);
    }
}
