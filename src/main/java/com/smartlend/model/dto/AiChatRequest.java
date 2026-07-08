package com.smartlend.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for AI chatbot for loan eligibility questions.
 * Customer can ask questions about loan requirements.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI chat request — ask questions about loan eligibility")
public class AiChatRequest {

    @NotBlank(message = "Message is required")
    @Size(min = 4, max = 1000, message = "Message must be minimum of 4 characters and less than 1000 characters")
    @Schema(
            description = "Your question about loan eligibility or requirements",
            example = "What credit score do I need to get a home loan approved?"
    )
    private String message;

    @Schema(
            description = "Optional — provide a loan application ID for context-aware responses",
            example = "42"
    )
    private Long loanApplicationId;
}