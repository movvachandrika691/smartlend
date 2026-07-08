package com.smartlend.controller;

import com.smartlend.model.dto.AiChatRequest;
import com.smartlend.model.dto.AiChatResponse;
import com.smartlend.model.dto.ApiResponse;
import com.smartlend.ai.LoanChatbotService;
import com.smartlend.ratelimit.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for AI-powered features.
 * Loan eligibility chatbot and risk assessment endpoints.
 */
@RestController
@RequestMapping(value = "/ai", produces = "application/json")
@RequiredArgsConstructor
@Tag(name = "AI Features", description = "AI-powered loan risk chat assistant")
@SecurityRequirement(name = "Bearer Authentication")
public class AiController {

    private final LoanChatbotService chatbotService;

    @PostMapping("/chat")
    @RateLimit(capacity = 5, refillTokens = 5, refillDurationMinutes = 1)
    @PreAuthorize("isAuthenticated()")
    @Operation(
            operationId = "01_chat",
            summary = "AI Chat",
            description = """
                    Chat with the AI assistant about loan eligibility and requirements.

                    **Access:** Any authenticated user (CUSTOMER, LOAN_OFFICER, ADMIN)

                    Ask questions like:
                    - "What credit score do I need for a home loan?"
                    - "Can I get a loan with a monthly income of 50,000?"
                    - "Why was my loan application rejected?"

                    Optionally pass a `loanApplicationId` to get context-aware responses \
                    about a specific application.

                    > Rate limited to **5 requests per minute**.

                    > Requires a valid AI API key configured in the server. \
                    If the AI service is unavailable, a fallback message is returned.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", 
                    description = "AI response returned successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", 
                    description = "Message is blank or too short/long",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", 
                    description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "429", 
                    description = "Rate limit exceeded",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<AiChatResponse>> chat(
            @Valid @RequestBody AiChatRequest request) {

        AiChatResponse response = chatbotService.chat(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
