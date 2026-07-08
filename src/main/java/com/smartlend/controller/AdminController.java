package com.smartlend.controller;

import com.smartlend.model.dto.ApiResponse;
import com.smartlend.model.entity.AuditLog;
import com.smartlend.service.LoanService;
import com.smartlend.repository.AuditLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for admin operations.
 * Dashboard statistics and audit log access.
 */
@RestController
@RequestMapping(value = "/admin", produces = "application/json")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Dashboard statistics and audit log access — ADMIN only")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminController {

    private final LoanService loanService;
    private final AuditLogRepository auditLogRepository;

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            operationId = "01_getStatistics",
            summary = "Get Loan Statistics",
            description = """
                    Returns a dashboard summary of all loan applications.

                    **Access:** ADMIN only

                    **Returns:**
                    - Total loans count
                    - Count by status: Pending, Under Review, Approved, Rejected
                    - Count of High Risk applications
                    - Overall approval rate (%)
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", 
                    description = "Statistics returned successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", 
                    description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", 
                    description = "ADMIN role required",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatistics() {
        LoanService.LoanStatistics stats = loanService.getStatistics();

        Map<String, Object> response = new HashMap<>();
        response.put("pending", stats.getPendingCount());
        response.put("underReview", stats.getUnderReviewCount());
        response.put("approved", stats.getApprovedCount());
        response.put("rejected", stats.getRejectedCount());
        response.put("highRisk", stats.getHighRiskCount());
        response.put("totalLoans", stats.getTotalLoans());
        response.put("approvalRate", stats.getApprovalRate());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            operationId = "02_getAuditLogs",
            summary = "Get Audit Logs",
            description = """
                    Returns paginated audit logs of all write operations in the system.

                    **Access:** ADMIN only

                    Audit logs are automatically recorded whenever a loan is submitted, \
                    approved, rejected, or when a user registers or logs in. \
                    Useful for compliance, investigation, and security monitoring.

                    **Pagination:** Use `?page=0&size=50&sort=timestamp,desc`
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", 
                    description = "Audit logs returned successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", 
                    description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", 
                    description = "ADMIN role required",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<Page<AuditLog>>> getAuditLogs(
            @PageableDefault(size = 50) Pageable pageable) {

        Page<AuditLog> logs = auditLogRepository.findAllByOrderByTimestampDesc(pageable);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }
}
