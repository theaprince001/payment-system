package com.payment.auth.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class LoginRequest {
    @NotBlank
    private String phoneNumber;
    @NotBlank
    private String password;
}