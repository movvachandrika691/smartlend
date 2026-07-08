package com.smartlend.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for loan officer approval/rejection decision.
 * Officer can add notes explaining the decision.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Loan decision request — used by LOAN_OFFICER to approve or reject")
public class LoanDecisionRequest {

    @NotBlank(message = "Notes is required")
    @Size(min = 2, max = 1000, message = "Notes must be minimum of 2 characters and less than 1000 characters")
    @Schema(
            description = "Officer notes explaining the approval or rejection decision",
            example = "Credit score and income are sufficient. Approved for requested amount."
    )
    private String notes;
}