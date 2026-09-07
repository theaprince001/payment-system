package com.payment.auth.service;

import com.payment.auth.dto.*;
import com.payment.auth.entity.User;
import com.payment.auth.exception.DuplicateUserException;
import com.payment.auth.repository.UserRepository;
import com.payment.auth.security.JwtService;
import com.payment.common.dto.UserProfileDto;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OtpService otpService;
    private final RestTemplate restTemplate;

    @Value("${USER_SERVICE_URL:http://localhost:8082}")
    private String userServiceUrl;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
            throw new DuplicateUserException("Phone number already registered");
        }

        User user = User.builder()
                .phoneNumber(request.getPhoneNumber())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .email(request.getEmail())
                .kycVerified(false)
                .active(true)
                .build();
        userRepository.save(user);

        // Create profile in User Service
        UserProfileDto profileDto = UserProfileDto.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .kycVerified(false)
                .build();

        try {
            restTemplate.postForEntity(userServiceUrl + "/api/users", profileDto, Void.class);
        } catch (Exception e) {
            // Log and continue; profile can be lazily created later
            log.warn("Failed to create user profile for {}: {}", user.getId(), e.getMessage());
        }

        String token = jwtService.generateToken(user.getId(), user.getPhoneNumber());
        return AuthResponse.builder()
                .userId(user.getId())
                .token(token)
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByPhoneNumber(request.getPhoneNumber())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid credentials");
        }

        String token = jwtService.generateToken(user.getId(), user.getPhoneNumber());
        return AuthResponse.builder()
                .userId(user.getId())
                .token(token)
                .build();
    }

    public void sendOtp(String phoneNumber) {
        // Check if user exists for login OTP
        String otp = otpService.generateOtp(phoneNumber);
        log.info("OTP for {}: {}", phoneNumber, otp); // Mock SMS
    }

    public AuthResponse verifyOtp(OtpVerifyRequest request) {
        if (!otpService.validateOtp(request.getPhoneNumber(), request.getOtp())) {
            throw new RuntimeException("Invalid OTP");
        }
        otpService.clearOtp(request.getPhoneNumber());

        User user = userRepository.findByPhoneNumber(request.getPhoneNumber())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String token = jwtService.generateToken(user.getId(), user.getPhoneNumber());
        return AuthResponse.builder()
                .userId(user.getId())
                .token(token)
                .build();
    }
}