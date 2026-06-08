package com.payment.common.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
public class CreatePaymentRequest extends IdempotentRequest {
    @NotNull
    private UUID payerId;
    @NotNull
    private UUID payeeId;
    @NotNull
    @Positive
    private BigDecimal amount;
    @NotNull
    private String paymentMethodId;
    private String description;
}