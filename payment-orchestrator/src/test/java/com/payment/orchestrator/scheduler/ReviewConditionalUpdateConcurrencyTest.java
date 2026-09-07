package com.payment.orchestrator.scheduler;

import com.payment.common.model.PaymentStatus;
import com.payment.orchestrator.entity.PaymentIntent;
import com.payment.orchestrator.repository.PaymentIntentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/payment_db",
        "spring.datasource.username=payment_user",
        "spring.datasource.password=StrongDbPass123!"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)   // 🔥 disable test transaction
public class ReviewConditionalUpdateConcurrencyTest {

    @Autowired
    private PaymentIntentRepository paymentIntentRepository;

    private UUID paymentId;

    @BeforeEach
    void setUp() {
        PaymentIntent intent = PaymentIntent.builder()
                .idempotencyKey(UUID.randomUUID().toString())
                .payerId(UUID.randomUUID())
                .payeeId(UUID.randomUUID())
                .amount(new BigDecimal("1000.00"))
                .paymentMethodId(UUID.randomUUID().toString())
                .status(PaymentStatus.PENDING_REVIEW)
                .riskDecision("REVIEW")
                .riskSource("ML")
                .riskScore(0.5)
                .build();
        intent = paymentIntentRepository.saveAndFlush(intent);
        paymentId = intent.getId();
    }

    @AfterEach
    void tearDown() {
        paymentIntentRepository.deleteById(paymentId);
    }

    @Test
    void concurrentReviewAndTimeout_OnlyOneTransitionSucceeds() throws Exception {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger skipCount = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final boolean approve = (i % 2 == 0);
            Future<?> future = executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                int updated = approve
                        ? paymentIntentRepository.updateStatusConditionally(
                        paymentId,
                        PaymentStatus.PENDING_REVIEW,
                        PaymentStatus.PENDING,
                        null,
                        "admin",
                        java.time.Instant.now())
                        : paymentIntentRepository.updateStatusConditionally(
                        paymentId,
                        PaymentStatus.PENDING_REVIEW,
                        PaymentStatus.FAILED,
                        "Review timeout – auto‑rejected",
                        "system",
                        java.time.Instant.now());
                if (updated == 1) {
                    successCount.incrementAndGet();
                } else {
                    skipCount.incrementAndGet();
                }
            });
            futures.add(future);
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        for (Future<?> future : futures) {
            future.get();
        }

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(skipCount.get()).isEqualTo(threads - 1);

        PaymentIntent finalIntent = paymentIntentRepository.findById(paymentId).orElseThrow();
        assertThat(finalIntent.getStatus()).isNotEqualTo(PaymentStatus.PENDING_REVIEW);
        assertThat(finalIntent.getStatus()).isIn(PaymentStatus.PENDING, PaymentStatus.FAILED);
    }
}