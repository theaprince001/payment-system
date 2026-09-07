package com.payment.common.dto;

import lombok.Data;

@Data
public class AddPaymentMethodRequest {
    private String type;
    private  String identifier;
}
