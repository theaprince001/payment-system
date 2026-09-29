[33mcommit a12577d5d2cda209d920984d5b463ea4bdddeee6[m[33m ([m[1;36mHEAD[m[33m -> [m[1;32mmain[m[33m, [m[1;31morigin/main[m[33m)[m
Author: Theaprince001 <ayansprince001@gmail.com>
Date:   Tue Sep 29 11:29:33 2026 +0530

    Add Razorpay provider using direct REST calls; introduce AWAITING_PROVIDER state
    
    - PaymentProvider interface implemented by MockPaymentProvider and RazorpayProvider
    - RazorpayProvider calls Razorpay's /v1/orders API directly via RestTemplate
      (removed razorpay-java SDK after it produced a stale Authentication failed error
       while the same credentials worked via curl)
    - Correlation ID (paymentIntentId) stored in order notes for later webhook matching
    - Payment flows now transition to AWAITING_PROVIDER after a Razorpay order is
      created and wait for a webhook to confirm before settling the ledger
    - Fixed a redelivery loop in LedgerEventConsumer and LedgerService by catching
      consumer exceptions and acknowledging instead of rethrowing

[1mdiff --git a/common/src/main/java/com/payment/common/model/PaymentStatus.java b/common/src/main/java/com/payment/common/model/PaymentStatus.java[m
[1mindex 6ad8821..ddb06b6 100644[m
[1m--- a/common/src/main/java/com/payment/common/model/PaymentStatus.java[m
[1m+++ b/common/src/main/java/com/payment/common/model/PaymentStatus.java[m
[36m@@ -1,5 +1,5 @@[m
 package com.payment.common.model;[m
 [m
 public enum PaymentStatus {[m
[31m-    CREATED, PENDING, SUCCESS, FAILED, REVERSED,PENDING_REVIEW[m
[32m+[m[32m    CREATED,AWAITING_PROVIDER, PENDING, SUCCESS, FAILED, REVERSED,PENDING_REVIEW[m
 }[m
\ No newline at end of file[m
[1mdiff --git a/docker-compose.yml b/docker-compose.yml[m
[1mindex 91a8bb9..63f41d0 100644[m
[1m--- a/docker-compose.yml[m
[1m+++ b/docker-compose.yml[m
[36m@@ -304,6 +304,30 @@[m [mservices:[m
       postgres:[m
         condition: service_healthy[m
 [m
[32m+[m[32m#  payment-orchestrator:[m
[32m+[m[32m#    build: ./payment-orchestrator[m
[32m+[m[32m#    container_name: payment-orchestrator[m
[32m+[m[32m#    restart: unless-stopped[m
[32m+[m[32m#    environment:[m
[32m+[m[32m#      SPRING_PROFILES_ACTIVE: prod[m
[32m+[m[32m#      DB_URL: jdbc:postgresql://postgres:5432/payment_db[m
[32m+[m[32m#      DB_USER: ${DB_USER}[m
[32m+[m[32m#      DB_PASSWORD: ${DB_PASSWORD}[m
[32m+[m[32m#      REDIS_HOST: redis[m
[32m+[m[32m#      REDIS_PASSWORD: ${REDIS_PASSWORD}[m
[32m+[m[32m#      RABBITMQ_HOST: rabbitmq[m
[32m+[m[32m#      RABBITMQ_USER: ${RABBITMQ_USER}[m
[32m+[m[32m#      RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD}[m
[32m+[m[32m#      SENTRY_DSN: ${SENTRY_DSN:-}[m
[32m+[m[32m#      PAYMENT_METHOD_SERVICE_URL: http://payment-method-service:8083[m
[32m+[m[32m#      FRAUD_SERVICE_URL: http://fraud-python-service:8000[m
[32m+[m[32m#    depends_on:[m
[32m+[m[32m#      postgres:[m
[32m+[m[32m#        condition: service_healthy[m
[32m+[m[32m#      redis:[m
[32m+[m[32m#        condition: service_healthy[m
[32m+[m[32m#      rabbitmq:[m
[32m+[m[32m#        condition: service_healthy[m
   payment-orchestrator:[m
     build: ./payment-orchestrator[m
     container_name: payment-orchestrator[m
[36m@@ -321,6 +345,10 @@[m [mservices:[m
       SENTRY_DSN: ${SENTRY_DSN:-}[m
       PAYMENT_METHOD_SERVICE_URL: http://payment-method-service:8083[m
       FRAUD_SERVICE_URL: http://fraud-python-service:8000[m
[32m+[m[32m      PAYMENT_PROVIDER: ${PAYMENT_PROVIDER}[m
[32m+[m[32m      RAZORPAY_KEY_ID: ${RAZORPAY_KEY_ID}[m
[32m+[m[32m      RAZORPAY_KEY_SECRET: ${RAZORPAY_KEY_SECRET}[m
[32m+[m[32m      RAZORPAY_WEBHOOK_SECRET: ${RAZORPAY_WEBHOOK_SECRET}[m
     depends_on:[m
       postgres:[m
         condition: service_healthy[m
[1mdiff --git a/ledger-service/src/main/java/com/payment/ledger/service/LedgerService.java b/ledger-service/src/main/java/com/payment/ledger/service/LedgerService.java[m
[1mindex 5a8d83e..3127de4 100644[m
[1m--- a/ledger-service/src/main/java/com/payment/ledger/service/LedgerService.java[m
[1m+++ b/ledger-service/src/main/java/com/payment/ledger/service/LedgerService.java[m
[36m@@ -9,6 +9,7 @@[m [mimport org.springframework.amqp.rabbit.annotation.RabbitListener;[m
 import org.springframework.amqp.rabbit.core.RabbitTemplate;[m
 import org.springframework.stereotype.Service;[m
 import org.springframework.transaction.annotation.Transactional;[m
[32m+[m
 import java.math.BigDecimal;[m
 import java.util.UUID;[m
 [m
[36m@@ -16,6 +17,7 @@[m [mimport java.util.UUID;[m
 @RequiredArgsConstructor[m
 @Slf4j[m
 public class LedgerService {[m
[32m+[m
     private final LedgerEntryRepository ledgerEntryRepository;[m
     private final RabbitTemplate rabbitTemplate;[m
 [m
[36m@@ -30,12 +32,20 @@[m [mpublic class LedgerService {[m
             }[m
             LedgerEntry entry = mapToEntity(debitDto);[m
             ledgerEntryRepository.save(entry);[m
[31m-            rabbitTemplate.convertAndSend("payment.exchange", "ledger.success", debitDto.getPaymentId());[m
[31m-        } catch (Exception e) {[m
[31m-            log.error("Debit failed: {}", e.getMessage());[m
[32m+[m[32m            rabbitTemplate.convertAndSend("payment.exchange", "ledger.success",[m
[32m+[m[32m                    debitDto.getPaymentId());[m
[32m+[m[32m        } catch (InsufficientBalanceException e) {[m
[32m+[m[32m            // Business failure — publish the failure, DO NOT rethrow.[m
[32m+[m[32m            log.warn("Debit rejected for payment {}: {}",[m
[32m+[m[32m                    debitDto.getPaymentId(), e.getMessage());[m
             rabbitTemplate.convertAndSend("payment.exchange", "ledger.failure",[m
                     new LedgerFailureEvent(debitDto.getPaymentId(), e.getMessage()));[m
[31m-            throw e;[m
[32m+[m[32m        } catch (Exception e) {[m
[32m+[m[32m            // Unexpected failure — log, publish failure, ack. Do not rethrow.[m
[32m+[m[32m            log.error("Unexpected error processing debit for payment {}",[m
[32m+[m[32m                    debitDto.getPaymentId(), e);[m
[32m+[m[32m            rabbitTemplate.convertAndSend("payment.exchange", "ledger.failure",[m
[32m+[m[32m                    new LedgerFailureEvent(debitDto.getPaymentId(), "Internal ledger error"));[m
         }[m
     }[m
 [m
[36m@@ -47,10 +57,10 @@[m [mpublic class LedgerService {[m
             LedgerEntry entry = mapToEntity(creditDto);[m
             ledgerEntryRepository.save(entry);[m
         } catch (Exception e) {[m
[31m-            log.error("Credit failed: {}", e.getMessage());[m
[32m+[m[32m            log.error("Unexpected error processing credit for payment {}",[m
[32m+[m[32m                    creditDto.getPaymentId(), e);[m
             rabbitTemplate.convertAndSend("payment.exchange", "ledger.failure",[m
[31m-                    new LedgerFailureEvent(creditDto.getPaymentId(), e.getMessage()));[m
[31m-            throw e;[m
[32m+[m[32m                    new LedgerFailureEvent(creditDto.getPaymentId(), "Internal ledger error"));[m
         }[m
     }[m
 [m
[36m@@ -68,7 +78,9 @@[m [mpublic class LedgerService {[m
     }[m
 [m
     public static class InsufficientBalanceException extends RuntimeException {[m
[31m-        public InsufficientBalanceException(String message) { super(message); }[m
[32m+[m[32m        public InsufficientBalanceException(String message) {[m
[32m+[m[32m            super(message);[m
[32m+[m[32m        }[m
     }[m
 [m
     public record LedgerFailureEvent(UUID paymentId, String reason) {}[m
[1mdiff --git a/payment-orchestrator/pom.xml b/payment-orchestrator/pom.xml[m
[1mindex efd0ac2..4741e5c 100644[m
[1m--- a/payment-orchestrator/pom.xml[m
[1m+++ b/payment-orchestrator/pom.xml[m
[36m@@ -51,11 +51,7 @@[m
             <groupId>org.assertj</groupId>[m
             <artifactId>assertj-core</artifactId>[m
         </dependency>[m
[31m-        <dependency>[m
[31m-            <groupId>com.razorpay</groupId>[m
[31m-            <artifactId>razorpay-java</artifactId>[m
[31m-            <version>1.4.5</version>[m
[31m-        </dependency>[m
[32m+[m
 [m
     </dependencies>[m
     <build><plugins><plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin>[m
[1mdiff --git a/payment-orchestrator/src/main/java/com/payment/orchestrator/entity/PaymentIntent.java b/payment-orchestrator/src/main/java/com/payment/orchestrator/entity/PaymentIntent.java[m
[1mindex 4cb43bb..2a29d58 100644[m
[1m--- a/payment-orchestrator/src/main/java/com/payment/orchestrator/entity/PaymentIntent.java[m
[1m+++ b/payment-orchestrator/src/main/java/com/payment/orchestrator/entity/PaymentIntent.java[m
[36m@@ -49,12 +49,14 @@[m [mpublic class PaymentIntent {[m
 [m
     private String riskDecision;     // ALLOW, REVIEW, BLOCK[m
     private String riskSource;       // ML, FALLBACK, RULE, ERROR[m
[31m-    private double riskScore;[m
[32m+[m[32m    private Double riskScore;[m
 [m
     private String reviewedBy;[m
     private Instant reviewedAt;[m
     private String reviewNote;[m
 [m
[32m+[m[32m    private String providerOrderId;[m
[32m+[m
     @PrePersist[m
     protected void onCreate() {[m
         createdAt = Instant.now();[m
[1mdiff --git a/payment-orchestrator/src/main/java/com/payment/orchestrator/messaging/LedgerEventConsumer.java b/payment-orchestrator/src/main/java/com/payment/orchestrator/messaging/LedgerEventConsumer.java[m
[1mindex 50a4319..53839a9 100644[m
[1m--- a/payment-orchestrator/src/main/java/com/payment/orchestrator/messaging/LedgerEventConsumer.java[m
[1m+++ b/payment-orchestrator/src/main/java/com/payment/orchestrator/messaging/LedgerEventConsumer.java[m
[36m@@ -5,25 +5,42 @@[m [mimport lombok.RequiredArgsConstructor;[m
 import lombok.extern.slf4j.Slf4j;[m
 import org.springframework.amqp.rabbit.annotation.RabbitListener;[m
 import org.springframework.stereotype.Component;[m
[32m+[m
 import java.util.UUID;[m
 [m
 @Component[m
 @RequiredArgsConstructor[m
 @Slf4j[m
 public class LedgerEventConsumer {[m
[32m+[m
     private final PaymentOrchestratorService orchestratorService;[m
 [m
     @RabbitListener(queues = "ledger.success.queue")[m
     public void onLedgerSuccess(UUID paymentId) {[m
[31m-        log.info("Received ledger success for payment: {}", paymentId);[m
[31m-        orchestratorService.handleLedgerSuccess(paymentId);[m
[32m+[m[32m        try {[m
[32m+[m[32m            log.info("Received ledger success for payment: {}", paymentId);[m
[32m+[m[32m            orchestratorService.handleLedgerSuccess(paymentId);[m
[32m+[m[32m        } catch (Exception e) {[m
[32m+[m[32m            // Ack and drop — a deterministic error on this message will never[m
[32m+[m[32m            // succeed on retry. Rethrowing would cause an infinite redelivery loop.[m
[32m+[m[32m            log.error("Failed to process ledger success for payment {}. "[m
[32m+[m[32m                    + "Acking and dropping to prevent redelivery loop.", paymentId, e);[m
[32m+[m[32m        }[m
     }[m
 [m
     @RabbitListener(queues = "ledger.failure.queue")[m
     public void onLedgerFailure(LedgerFailureEvent event) {[m
[31m-        log.info("Received ledger failure for payment: {}, reason: {}",[m
[31m-                event.paymentId(), event.reason());[m
[31m-        orchestratorService.handleLedgerFailure(event.paymentId(), event.reason());[m
[32m+[m[32m        try {[m
[32m+[m[32m            log.info("Received ledger failure for payment: {}, reason: {}",[m
[32m+[m[32m                    event.paymentId(), event.reason());[m
[32m+[m[32m            orchestratorService.handleLedgerFailure(event.paymentId(), event.reason());[m
[32m+[m[32m        } catch (Exception e) {[m
[32m+[m[32m            // Same reasoning as above — never rethrow from a consumer, or RabbitMQ[m
[32m+[m[32m            // will requeue the message indefinitely and starve healthy traffic.[m
[32m+[m[32m            log.error("Failed to process ledger failure for payment {}. "[m
[32m+[m[32m                            + "Acking and dropping to prevent redelivery loop.",[m
[32m+[m[32m                    event.paymentId(), e);[m
[32m+[m[32m        }[m
     }[m
 [m
     public record LedgerFailureEvent(UUID paymentId, String reason) {}[m
[1mdiff --git a/payment-orchestrator/src/main/java/com/payment/orchestrator/provider/MockPaymentProvider.java b/payment-orchestrator/src/main/java/com/payment/orchestrator/provider/MockPaymentProvider.java[m
[1mindex 0e4466d..87de19b 100644[m
[1m--- a/payment-orchestrator/src/main/java/com/payment/orchestrator/provider/MockPaymentProvider.java[m
[1m+++ b/payment-orchestrator/src/main/java/com/payment/orchestrator/provider/MockPaymentProvider.java[m
[36m@@ -16,7 +16,7 @@[m [mpublic class MockPaymentProvider implements PaymentProvider {[m
     public ProviderResponse initiatePayment(PaymentIntent intent) {[m
         String mockOrderId = "mock_order_" + UUID.randomUUID();[m
         log.info("Mock provider created order {} for intent {}", mockOrderId, intent.getId());[m
[31m-        return new ProviderResponse(mockOrderId, true);[m
[32m+[m[32m        return new ProviderResponse(mockOrderId, true, false);   // no webhook needed[m
     }[m
 [m
     @Override[m
[1mdiff --git a/payment-orchestrator/src/main/java/com/payment/orchestrator/provider/ProviderResponse.java b/payment-orchestrator/src/main/java/com/payment/orchestrator/provider/ProviderResponse.java[m
[1mindex a068a57..29e05d3 100644[m
[1m--- a/payment-orchestrator/src/main/java/com/payment/orchestrator/provider/ProviderResponse.java[m
[1m+++ b/payment-orchestrator/src/main/java/com/payment/orchestrator/provider/ProviderResponse.java[m
[36m@@ -1,3 +1,6 @@[m
 package com.payment.orchestrator.provider;[m
 [m
[31m-public record ProviderResponse(String providerOrderId, boolean success) {}[m
\ No newline at end of file[m
[32m+[m[32mpublic record ProviderResponse([m
[32m+[m[32m        String providerOrderId,[m
[32m+[m[32m        boolean success,[m
[32m+[m[32m        boolean requiresWebhookConfirmation) {}[m
\ No newline at end of file[m
[1mdiff --git a/payment-orchestrator/src/main/java/com/payment/orchestrator/provider/RazorpayProvider.java b/payment-orchestrator/src/main/java/com/payment/orchestrator/provider/RazorpayProvider.java[m
[1mindex 5f8d315..6eb33c7 100644[m
[1m--- a/payment-orchestrator/src/main/java/com/payment/orchestrator/provider/RazorpayProvider.java[m
[1m+++ b/payment-orchestrator/src/main/java/com/payment/orchestrator/provider/RazorpayProvider.java[m
[36m@@ -1,20 +1,95 @@[m
 package com.payment.orchestrator.provider;[m
 [m
 import com.payment.orchestrator.entity.PaymentIntent;[m
[32m+[m[32mimport lombok.extern.slf4j.Slf4j;[m
[32m+[m[32mimport org.springframework.beans.factory.annotation.Value;[m
 import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;[m
[32m+[m[32mimport org.springframework.http.HttpEntity;[m
[32m+[m[32mimport org.springframework.http.HttpHeaders;[m
[32m+[m[32mimport org.springframework.http.MediaType;[m
[32m+[m[32mimport org.springframework.http.ResponseEntity;[m
 import org.springframework.stereotype.Component;[m
[32m+[m[32mimport org.springframework.web.client.RestClientException;[m
[32m+[m[32mimport org.springframework.web.client.RestTemplate;[m
[32m+[m
[32m+[m[32mimport java.math.BigDecimal;[m
[32m+[m[32mimport java.util.HashMap;[m
[32m+[m[32mimport java.util.Map;[m
 [m
 @Component[m
 @ConditionalOnProperty(name = "payment.provider", havingValue = "razorpay")[m
[32m+[m[32m@Slf4j[m
 public class RazorpayProvider implements PaymentProvider {[m
 [m
[32m+[m[32m    private static final String RAZORPAY_ORDERS_URL = "https://api.razorpay.com/v1/orders";[m
[32m+[m
[32m+[m[32m    private final RestTemplate restTemplate;[m
[32m+[m[32m    private final String keyId;[m
[32m+[m[32m    private final String keySecret;[m
[32m+[m[32m    private final String webhookSecret;[m
[32m+[m
[32m+[m[32m    public RazorpayProvider([m
[32m+[m[32m            RestTemplate restTemplate,[m
[32m+[m[32m            @Value("${razorpay.key.id}") String keyId,[m
[32m+[m[32m            @Value("${razorpay.key.secret}") String keySecret,[m
[32m+[m[32m            @Value("${razorpay.webhook.secret}") String webhookSecret) {[m
[32m+[m[32m        this.restTemplate = restTemplate;[m
[32m+[m[32m        this.keyId = keyId;[m
[32m+[m[32m        this.keySecret = keySecret;[m
[32m+[m[32m        this.webhookSecret = webhookSecret;[m
[32m+[m[32m        log.info("RazorpayProvider initialized with keyId={}", keyId);[m
[32m+[m[32m    }[m
[32m+[m
     @Override[m
     public ProviderResponse initiatePayment(PaymentIntent intent) {[m
[31m-        throw new UnsupportedOperationException("Not implemented yet — Session 2");[m
[32m+[m[32m        try {[m
[32m+[m[32m            HttpHeaders headers = new HttpHeaders();[m
[32m+[m[32m            headers.setContentType(MediaType.APPLICATION_JSON);[m
[32m+[m[32m            headers.setBasicAuth(keyId, keySecret);[m
[32m+[m
[32m+[m[32m            // Razorpay expects amount in paise (INR * 100)[m
[32m+[m[32m            int amountInPaise = intent.getAmount()[m
[32m+[m[32m                    .multiply(BigDecimal.valueOf(100))[m
[32m+[m[32m                    .intValue();[m
[32m+[m
[32m+[m[32m            Map<String, Object> body = new HashMap<>();[m
[32m+[m[32m            body.put("amount", amountInPaise);[m
[32m+[m[32m            body.put("currency", "INR");[m
[32m+[m
[32m+[m[32m            // Correlation ID — paymentIntentId stored in notes (no length limit)[m
[32m+[m[32m            Map<String, String> notes = new HashMap<>();[m
[32m+[m[32m            notes.put("paymentIntentId", intent.getId().toString());[m
[32m+[m[32m            body.put("notes", notes);[m
[32m+[m
[32m+[m[32m            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);[m
[32m+[m
[32m+[m[32m            ResponseEntity<Map> response = restTemplate.postForEntity([m
[32m+[m[32m                    RAZORPAY_ORDERS_URL, entity, Map.class);[m
[32m+[m
[32m+[m[32m            Map responseBody = response.getBody();[m
[32m+[m[32m            if (responseBody == null || responseBody.get("id") == null) {[m
[32m+[m[32m                log.error("Razorpay returned empty or invalid response for intent {}",[m
[32m+[m[32m                        intent.getId());[m
[32m+[m[32m                return new ProviderResponse(null, false, true);[m
[32m+[m[32m            }[m
[32m+[m
[32m+[m[32m            String orderId = responseBody.get("id").toString();[m
[32m+[m[32m            log.info("Razorpay order created: {} for intent {}", orderId, intent.getId());[m
[32m+[m[32m            return new ProviderResponse(orderId, true, true);[m
[32m+[m
[32m+[m[32m        } catch (RestClientException e) {[m
[32m+[m[32m            log.error("Failed to create Razorpay order for intent {}: {}",[m
[32m+[m[32m                    intent.getId(), e.getMessage());[m
[32m+[m[32m            return new ProviderResponse(null, false, true);[m
[32m+[m[32m        } catch (Exception e) {[m
[32m+[m[32m            log.error("Unexpected error creating Razorpay order for intent {}",[m
[32m+[m[32m                    intent.getId(), e);[m
[32m+[m[32m            return new ProviderResponse(null, false, true);[m
[32m+[m[32m        }[m
     }[m
 [m
     @Override[m
     public boolean verifyWebhookSignature(String rawBody, String signature) {[m
[31m-        throw new UnsupportedOperationException("Not implemented yet — Session 4");[m
[32m+[m[32m        throw new UnsupportedOperationException("Session 4");[m
     }[m
 }[m
\ No newline at end of file[m
[1mdiff --git a/payment-orchestrator/src/main/java/com/payment/orchestrator/service/PaymentOrchestratorService.java b/payment-orchestrator/src/main