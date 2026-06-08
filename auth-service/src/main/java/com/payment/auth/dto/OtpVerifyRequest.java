package com.payment.auth.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class OtpVerifyRequest {
    @NotBlank
    private String phoneNumber;
    @NotBlank
    private String otp;
}