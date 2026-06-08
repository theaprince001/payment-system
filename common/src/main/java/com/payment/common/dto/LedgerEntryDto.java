package com.payment.common.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntryDto {
    private UUID ledgerId;
    private UUID accountId;
    private BigDecimal amount;
    private UUID paymentId;
    private Instant timestamp;
}