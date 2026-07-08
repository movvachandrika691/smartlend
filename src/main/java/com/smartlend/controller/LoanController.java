package com.smartlend.controller;

import com.smartlend.model.dto.ApiResponse;
import com.smartlend.model.dto.LoanApplicationRequest;
import com.smartlend.model.dto.LoanApplicationResponse;
import com.smartlend.model.dto.LoanDecisionRequest;
import com.smartlend.model.enums.LoanStatus;
import com.smartlend.ratelimit.RateLimit;
import com.smartlend.service.LoanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.media.Content;
/**
 * Loan application management — submit, list, approve, reject.
 * All endpoints require a valid JWT token.
 */
@RestController
@RequestMapping(value = "/loans", produces = "application/json")
@RequiredArgsConstructor
@Tag(name = "Loan Management", description = "Submit loans, view applications, approve or reject")
@SecurityRequirement(name = "Bearer Authentication")
public class LoanController {

    private final LoanService loanService;

    // ─────────────────────────────────────────────
    // Submit Loan — CUSTOMER only
    // ─────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @RateLimit(capacity = 5, refillTokens = 5, refillDurationMinutes = 1)
    @Operation(
            operationId = "01_submitLoan",
            summary = "Submit Loan Application",
            description = """
                    Submit a new loan application as a **CUSTOMER**.

                    After submission:
                    1. Application is saved with status `PENDING`
                    2. AI risk assessment runs automatically
                    3. Status moves to `UNDER_REVIEW` with a risk level (`LOW`, `MEDIUM`, `HIGH`)
                    4. A loan officer reviews and approves or rejects

                    > Rate limited to **5 requests per minute**.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Loan submitted — AI risk assessment completed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error or loan amount out of allowed range", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Only CUSTOMER role can submit loans", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<LoanApplicationResponse>> submitLoan(
            @Valid @RequestBody LoanApplicationRequest request,
            @AuthenticationPrincipal Long userId) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Loan application submitted successfully",
                        loanService.submitLoan(userId, request)
                )
        );
    }

    // ─────────────────────────────────────────────
    // Get All Loans — LOAN_OFFICER / ADMIN only
    // ─────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('LOAN_OFFICER', 'ADMIN')")
    @Operation(
            operationId = "02_getAllLoans",
            summary = "Get All Loans",
            description = """
                    Paginated list of all loan applications.

                    **Access:** `LOAN_OFFICER`, `ADMIN`

                    **Filtering:** Pass `?status=PENDING` to filter by loan status.

                    **Pagination:** Use `?page=0&size=20&sort=createdAt,desc`

                    Allowed status values: `PENDING`, `UNDER_REVIEW`, `APPROVED`, `REJECTED`
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Loans returned successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid status value", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "CUSTOMER role cannot access this endpoint", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<Page<LoanApplicationResponse>>> getAllLoans(
            @PageableDefault(size = 20) Pageable pageable,
            @Parameter(
                    description = "Filter by loan status",
                    schema = @Schema(
                            type = "string",
                            allowableValues = {"PENDING", "UNDER_REVIEW", "APPROVED", "REJECTED"}
                    )
            )
            @RequestParam(required = false) LoanStatus status) {

        return ResponseEntity.ok(
                ApiResponse.success(loanService.getAllLoans(pageable, status))
        );
    }

    // ─────────────────────────────────────────────
    // Get Loan by ID — all roles with auth check
    // ─────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            operationId = "03_getLoanById",
            summary = "Get Loan by ID",
            description = """
                    Fetch a single loan application by ID.

                    **Access rules:**
                    - `CUSTOMER` — can only view their own loans (403 if they try another's)
                    - `LOAN_OFFICER`, `ADMIN` — can view any loan
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Loan found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Customer attempting to view another customer's loan", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Loan not found", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<LoanApplicationResponse>> getLoanById(
            @PathVariable Long id,
            Authentication authentication) {

        Long userId = (Long) authentication.getPrincipal();
        String role = authentication.getAuthorities()
                .iterator()
                .next()
                .getAuthority()
                .replace("ROLE_", "");

        return ResponseEntity.ok(
                ApiResponse.success(loanService.getLoanById(id, userId, role))
        );
    }

    // ─────────────────────────────────────────────
    // Get My Loans — CUSTOMER only
    // ─────────────────────────────────────────────

    @GetMapping("/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
            operationId = "04_getMyLoans",
            summary = "Get My Loans",
            description = """
                    View your own loan applications as a **CUSTOMER**.

                    Results are paginated and sorted by submission date (newest first).

                    **Pagination:** `?page=0&size=10&sort=createdAt,desc`

                    Results are cached in Redis for 5 minutes for performance.
                    Cache is automatically cleared when you submit a new loan or
                    when a loan officer updates the status of any of your loans.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Your loans returned successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Only CUSTOMER role can access this endpoint", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<Page<LoanApplicationResponse>>> getMyLoans(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {

        return ResponseEntity.ok(
                ApiResponse.success(loanService.getCustomerLoans(userId, pageable))
        );
    }

    // ─────────────────────────────────────────────
    // Approve Loan — LOAN_OFFICER only
    // ─────────────────────────────────────────────

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('LOAN_OFFICER')")
    @Operation(
            operationId = "05_approveLoan",
            summary = "Approve Loan",
            description = """
                    Approve a loan application as a **LOAN_OFFICER**.

                    - Status changes from `PENDING` or `UNDER_REVIEW` → `APPROVED`
                    - Officer notes are required
                    - Cannot approve a loan that is already `APPROVED` or `REJECTED`

                    Customer's loan cache is automatically invalidated on approval.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Loan approved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Loan is already approved or rejected", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Only LOAN_OFFICER can approve loans", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Loan not found", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<LoanApplicationResponse>> approveLoan(
            @PathVariable Long id,
            @RequestBody(required = false) LoanDecisionRequest request,
            @AuthenticationPrincipal Long userId) {

        String notes = request != null ? request.getNotes() : null;
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Loan approved successfully",
                        loanService.approveLoan(id, userId, notes)
                )
        );
    }

    // ─────────────────────────────────────────────
    // Reject Loan — LOAN_OFFICER only
    // ─────────────────────────────────────────────

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('LOAN_OFFICER')")
    @Operation(
            operationId = "06_rejectLoan",
            summary = "Reject Loan",
            description = """
                    Reject a loan application as a **LOAN_OFFICER**.

                    - Status changes from `PENDING` or `UNDER_REVIEW` → `REJECTED`
                    - Officer notes are required
                    - Cannot reject a loan that is already `APPROVED` or `REJECTED`

                    Customer's loan cache is automatically invalidated on rejection.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Loan rejected successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Loan is already approved or rejected", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Only LOAN_OFFICER can reject loans", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Loan not found", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<LoanApplicationResponse>> rejectLoan(
            @PathVariable Long id,
            @RequestBody(required = false) LoanDecisionRequest request,
            @AuthenticationPrincipal Long userId) {

        String notes = request != null ? request.getNotes() : null;
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Loan rejected successfully",
                        loanService.rejectLoan(id, userId, notes)
                )
        );
    }
}