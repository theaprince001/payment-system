package com.payment.common.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.validation.constraints.AssertTrue;
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

    // Optional if using existing payment method
    private String paymentMethodId;

    // New payment method fields (used if paymentMethodId is null)
    private String paymentMethodType;         // CARD, UPI, BANK
    private String paymentMethodIdentifier;   // card number, UPI ID, bank account
    private boolean savePaymentMethod;

    @AssertTrue(message = "Either paymentMethodId or paymentMethodType+paymentMethodIdentifier must be provided")
    public boolean isPaymentMethodValid() {
        if (paymentMethodId != null && !paymentMethodId.isEmpty()) {
            return true;
        }
        return paymentMethodType != null && !paymentMethodType.isEmpty()
                && paymentMethodIdentifier != null && !paymentMethodIdentifier.isEmpty();
    }
}