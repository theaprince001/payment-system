package com.payment.orchestrator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.common.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private static final String KEY_PREFIX = "idem:";
    private static final Duration TTL = Duration.ofHours(24);

    public Optional<PaymentResponse> get(String idempotencyKey) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + idempotencyKey);
        if (value != null) {
            try {
                return Optional.of(objectMapper.readValue(value, PaymentResponse.class));
            } catch (Exception e) {
                log.error("Failed to deserialize idempotency response", e);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public boolean save(String idempotencyKey, PaymentResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            Boolean success = redisTemplate.opsForValue()
                    .setIfAbsent(KEY_PREFIX + idempotencyKey, json, TTL);
            return Boolean.TRUE.equals(success);
        } catch (Exception e) {
            log.error("Failed to save idempotency response", e);
            return false;
        }
    }
}