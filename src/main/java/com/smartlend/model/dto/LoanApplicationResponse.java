package com.smartlend.model.dto;

import com.smartlend.model.enums.LoanStatus;
import com.smartlend.model.enums.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for loan application data sent to clients.
 * Excludes sensitive internal fields like soft delete timestamps.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanApplicationResponse {

    private Long id;
    private Long customerId;
    private String customerName;
    private String customerEmail;
    private BigDecimal loanAmount;
    private String purpose;
    private BigDecimal monthlyIncome;
    private String employmentType;
    private Integer creditScore;

    // AI-generated assessment
    private RiskLevel riskLevel;
    private String aiRiskReason;

    private LoanStatus status;
    private Long officerId;
    private String officerName;
    private String officerNotes;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime reviewedAt;
}
