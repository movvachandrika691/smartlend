package com.smartlend.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for submitting a new loan application.
 * Customer provides financial details for risk assessment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Loan application — submitted by CUSTOMER role only")
public class LoanApplicationRequest {

    @NotNull(message = "Loan amount is required")
    @DecimalMin(value = "10000", message = "Minimum loan amount is 10,000")
    @DecimalMax(value = "50000000", message = "Maximum loan amount is 50,000,000")
    @Schema(
            description = "Loan amount in INR. Min: 10,000 — Max: 50,000,000",
            example = "500000"
    )
    private BigDecimal loanAmount;

    @NotBlank(message = "Purpose is required")
    @Size(max = 500, message = "Purpose must be less than 500 characters")
    @Schema(
            description = "Purpose of the loan",
            example = "Home renovation"
    )
    private String purpose;

    @NotNull(message = "Monthly income is required")
    @DecimalMin(value = "5000", message = "Monthly income must be at least 5,000")
    @Schema(
            description = "Applicant monthly income in INR",
            example = "75000"
    )
    private BigDecimal monthlyIncome;

    @NotBlank(message = "Employment type is required")
    @Pattern(regexp = "SALARIED|SELF_EMPLOYED|BUSINESS", message = "Employment type must be SALARIED, SELF_EMPLOYED, or BUSINESS")
    @Schema(
            description = "Employment type of the applicant",
            example = "SALARIED",
            allowableValues = {"SALARIED", "SELF_EMPLOYED", "BUSINESS"}
    )
    private String employmentType;

    @NotNull(message = "Credit score is required")
    @Min(value = 300, message = "Credit score must be at least 300")
    @Max(value = 900, message = "Credit score cannot exceed 900")
    @Schema(
            description = "Applicant credit score. Min: 300 — Max: 900",
            example = "750"
    )
    private Integer creditScore;
}