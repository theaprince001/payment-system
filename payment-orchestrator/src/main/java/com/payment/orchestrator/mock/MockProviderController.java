package com.payment.orchestrator.mock;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/mock/provider")
public class MockProviderController {
    private final Map<String, ProviderBehavior> behaviors = new ConcurrentHashMap<>();

    public enum ProviderType { CARD, UPI, BANK }

    public enum ProviderBehavior {
        SUCCESS(0), FAILURE(100), TIMEOUT(5000), SLOW(2000);
        final int delayMs;
        ProviderBehavior(int delayMs) { this.delayMs = delayMs; }
    }

    @PostMapping("/{type}/process")
    public ResponseEntity<ProviderResponse> processPayment(
            @PathVariable ProviderType type,
            @RequestBody ProviderRequest request) {
        ProviderBehavior behavior = behaviors.getOrDefault(type.name(), ProviderBehavior.SUCCESS);
        try {
            Thread.sleep(behavior.delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (behavior == ProviderBehavior.FAILURE) {
            return ResponseEntity.ok(ProviderResponse.failure("Provider declined"));
        }
        return ResponseEntity.ok(ProviderResponse.success("txn_" + UUID.randomUUID().toString().substring(0, 8)));
    }

    @PostMapping("/behavior")
    public void setBehavior(@RequestParam ProviderType type, @RequestParam ProviderBehavior behavior) {
        behaviors.put(type.name(), behavior);
    }

    @PostMapping("/behavior/reset")
    public void resetAll() {
        behaviors.clear();
    }

    public record ProviderRequest(UUID paymentId, BigDecimal amount, String token) {}
    public record ProviderResponse(boolean success, String transactionId, String errorMessage) {
        public static ProviderResponse success(String txnId) {
            return new ProviderResponse(true, txnId, null);
        }
        public static ProviderResponse failure(String error) {
            return new ProviderResponse(false, null, error);
        }
    }
}