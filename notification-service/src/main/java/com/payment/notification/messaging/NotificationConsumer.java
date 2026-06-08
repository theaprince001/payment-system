package com.payment.notification.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@Slf4j
public class NotificationConsumer {
    @RabbitListener(queues = "payment.completed.queue")
    public void onPaymentCompleted(UUID paymentId) {
        log.info("Payment completed: {}. Sending notification...", paymentId);
    }

    @RabbitListener(queues = "payment.failed.queue")
    public void onPaymentFailed(PaymentFailedEvent event) {
        log.info("Payment failed: {}. Reason: {}. Sending notification...", event.paymentId(), event.reason());
    }

    public record PaymentFailedEvent(UUID paymentId, String reason) {}
}