package com.payment.orchestrator.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    public static final String EXCHANGE = "payment.exchange";
    public static final String LEDGER_DEBIT_QUEUE = "ledger.debit.queue";
    public static final String LEDGER_CREDIT_QUEUE = "ledger.credit.queue";
    public static final String LEDGER_SUCCESS_QUEUE = "ledger.success.queue";
    public static final String LEDGER_FAILURE_QUEUE = "ledger.failure.queue";
    public static final String PAYMENT_COMPLETED_QUEUE = "payment.completed.queue";
    public static final String PAYMENT_FAILED_QUEUE = "payment.failed.queue";

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue ledgerDebitQueue() { return new Queue(LEDGER_DEBIT_QUEUE, true); }
    @Bean
    public Queue ledgerCreditQueue() { return new Queue(LEDGER_CREDIT_QUEUE, true); }
    @Bean
    public Queue ledgerSuccessQueue() { return new Queue(LEDGER_SUCCESS_QUEUE, true); }
    @Bean
    public Queue ledgerFailureQueue() { return new Queue(LEDGER_FAILURE_QUEUE, true); }
    @Bean
    public Queue paymentCompletedQueue() { return new Queue(PAYMENT_COMPLETED_QUEUE, true); }
    @Bean
    public Queue paymentFailedQueue() { return new Queue(PAYMENT_FAILED_QUEUE, true); }

    @Bean
    public Binding ledgerDebitBinding() {
        return BindingBuilder.bind(ledgerDebitQueue()).to(exchange()).with("ledger.debit");
    }
    @Bean
    public Binding ledgerCreditBinding() {
        return BindingBuilder.bind(ledgerCreditQueue()).to(exchange()).with("ledger.credit");
    }
    @Bean
    public Binding ledgerSuccessBinding() {
        return BindingBuilder.bind(ledgerSuccessQueue()).to(exchange()).with("ledger.success");
    }
    @Bean
    public Binding ledgerFailureBinding() {
        return BindingBuilder.bind(ledgerFailureQueue()).to(exchange()).with("ledger.failure");
    }
    @Bean
    public Binding paymentCompletedBinding() {
        return BindingBuilder.bind(paymentCompletedQueue()).to(exchange()).with("payment.completed");
    }
    @Bean
    public Binding paymentFailedBinding() {
        return BindingBuilder.bind(paymentFailedQueue()).to(exchange()).with("payment.failed");
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}