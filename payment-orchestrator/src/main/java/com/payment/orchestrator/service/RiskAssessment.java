package com.payment.orchestrator.service;

public record RiskAssessment(double score, String decision, String source) {
    public static final String DECISION_ALLOW = "ALLOW";
    public static final String DECISION_REVIEW = "REVIEW";
    public static final String DECISION_BLOCK = "BLOCK";
    public static final String DECISION_SYSTEM_ERROR = "SYSTEM_ERROR";
}