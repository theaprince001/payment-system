package com.payment.auth.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class RegisterRequest {
    @NotBlank
    private String phoneNumber;
    @NotBlank
    private String password;
    private String fullName;
    private String email;
}