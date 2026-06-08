package com.payment.paymentmethod.dto;

import com.payment.paymentmethod.entity.PaymentMethod.PaymentMethodType;
import lombok.Data;

@Data
public class AddPaymentMethodRequest {
    private PaymentMethodType type;
    private String identifier;
}