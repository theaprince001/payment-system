package com.payment.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RiskResponse {
    private double score;
    private String decision;   // ALLOW, REVIEW, BLOCK, FALLBACK (when fallback=true)
    private boolean fallback;
}