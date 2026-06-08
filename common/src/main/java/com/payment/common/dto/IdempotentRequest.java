package com.payment.common.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class IdempotentRequest {
    @NotBlank
    private String idempotencyKey;
}