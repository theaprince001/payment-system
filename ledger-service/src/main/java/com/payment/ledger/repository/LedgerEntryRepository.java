package com.payment.ledger.repository;

import com.payment.ledger.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    @Query("SELECT SUM(l.amount) FROM LedgerEntry l WHERE l.accountId = :accountId")
    Optional<BigDecimal> sumAmountByAccountId(@Param("accountId") UUID accountId);
}