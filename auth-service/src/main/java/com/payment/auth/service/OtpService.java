package com.payment.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {
    private final StringRedisTemplate redisTemplate;
    private static final String OTP_PREFIX = "otp:";
    private static final Duration OTP_TTL = Duration.ofMinutes(5);

    public String generateOtp(String phoneNumber) {
        String otp = String.format("%06d", new Random().nextInt(999999));
        redisTemplate.opsForValue().set(OTP_PREFIX + phoneNumber, otp, OTP_TTL);
        log.info("OTP for {}: {}", phoneNumber, otp); // Mock SMS
        return otp;
    }

    public boolean validateOtp(String phoneNumber, String otp) {
        String storedOtp = redisTemplate.opsForValue().get(OTP_PREFIX + phoneNumber);
        return otp.equals(storedOtp);
    }

    public void clearOtp(String phoneNumber) {
        redisTemplate.delete(OTP_PREFIX + phoneNumber);
    }
}