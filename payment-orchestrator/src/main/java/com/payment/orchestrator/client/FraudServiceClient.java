package com.payment.orchestrator.client;

import com.payment.common.dto.RiskRequest;
import com.payment.common.dto.RiskResponse;
import com.payment.orchestrator.exception.FraudServiceInternalException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class FraudServiceClient {

    private final RestTemplate restTemplate;

    @Value("${FRAUD_SERVICE_URL:http://localhost:8000}")
    private String fraudServiceUrl;

    @CircuitBreaker(name = "fraudService", fallbackMethod = "fraudFallback")
    public RiskResponse callFraudService(RiskRequest riskRequest) {
        ResponseEntity<RiskResponse> response;
        try {
            response = restTemplate.postForEntity(
                    fraudServiceUrl + "/predict",
                    riskRequest,
                    RiskResponse.class
            );
        } catch (RestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new FraudServiceInternalException("Unexpected error in fraud service client", e);
        }

        RiskResponse body = response.getBody();
        if (body == null) {
            throw new FraudServiceInternalException("Fraud service returned empty body", null);
        }

        body.setFallback(false);
        return body;
    }

    private RiskResponse fraudFallback(RiskRequest riskRequest, Throwable t) {
        double score = ruleBasedRiskScore(riskRequest);
        String decision = score > 0.7 ? "BLOCK" :
                score > 0.3 ? "REVIEW" : "ALLOW";
        return new RiskResponse(score, decision, true);  // fallback=true
    }

    private double ruleBasedRiskScore(RiskRequest request) {
        double score = 0.0;
        if (request.getAmount().doubleValue() > 50000) score += 0.4;
        if (request.getPaymentMethodAgeSeconds() < 60) score += 0.2;
        if (request.getTransactionVelocity() > 5) score += 0.3;
        return Math.min(1.0, score);
    }
}