package com.payment.common.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class RiskRequest {
    private String payerId;
    private String payeeId;
    private BigDecimal amount;
    private String paymentMethodType;
    private String paymentMethodIdentifier;
    private int userAccountAgeDays;
    private int paymentMethodAgeSeconds;
    private int transactionVelocity;
}