package com.payment.orchestrator.exception;

public class FraudServiceInternalException extends RuntimeException {
    public FraudServiceInternalException(String message, Throwable cause) {
        super(message, cause);
    }
}