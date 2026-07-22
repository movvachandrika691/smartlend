package com.smartlend.service;

import com.smartlend.exception.BadRequestException;
import com.smartlend.exception.ResourceNotFoundException;
import com.smartlend.exception.UnauthorizedException;
import com.smartlend.model.dto.LoanApplicationRequest;
import com.smartlend.model.dto.LoanApplicationResponse;
import com.smartlend.model.entity.LoanApplication;
import com.smartlend.model.entity.User;
import com.smartlend.model.enums.LoanStatus;
import com.smartlend.model.enums.RiskLevel;
import com.smartlend.repository.LoanApplicationRepository;
import com.smartlend.repository.UserRepository;
import com.smartlend.ai.RiskAssessmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service layer for Loan Application business logic.
 *
 * Caching strategy:
 * We cache List<LoanApplicationResponse> instead of Page<T> in Redis.
 * Page<T> is not safely serializable/deserializable without activateDefaultTyping,
 * which causes @class metadata to leak into HTTP responses.
 * After a cache hit, Page<T> is reconstructed from the cached list + pageable.
 *
 * Pagination strategy:
 * All list queries use a two-query approach — paginate IDs first at DB level,
 * then fetch full entities by those IDs with JOIN FETCH.
 * This avoids Hibernate's in-memory pagination bug when using JOIN FETCH + Pageable.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoanService {

    private final LoanApplicationRepository loanRepository;
    private final UserRepository userRepository;
    private final RiskAssessmentService riskAssessmentService;
    private final RedisCacheService redisCacheService;

    @Value("${app.loan.min-amount}")
    private BigDecimal minLoanAmount;

    @Value("${app.loan.max-amount}")
    private BigDecimal maxLoanAmount;

    /**
     * Submit a new loan application - CUSTOMER only.
     */
    @Transactional
    public LoanApplicationResponse submitLoan(Long customerId, LoanApplicationRequest request) {
        log.info("Submitting loan application for customer ID: {}", customerId);

        if (request.getLoanAmount().compareTo(minLoanAmount) < 0 ||
            request.getLoanAmount().compareTo(maxLoanAmount) > 0) {
            throw new BadRequestException(
                    String.format("Loan amount must be between %s and %s", minLoanAmount, maxLoanAmount));
        }

        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", customerId));

        LoanApplication loan = LoanApplication.builder()
                .customer(customer)
                .loanAmount(request.getLoanAmount())
                .purpose(request.getPurpose())
                .monthlyIncome(request.getMonthlyIncome())
                .employmentType(request.getEmploymentType())
                .creditScore(request.getCreditScore())
                .status(LoanStatus.PENDING)
                .build();

        loan = loanRepository.save(loan);

        try {
            RiskAssessmentService.RiskAssessment assessment =
                    riskAssessmentService.assessRisk(loan);
            loan.setRiskLevel(assessment.getRiskLevel());
            loan.setAiRiskReason(assessment.getReason());
            loan.setStatus(LoanStatus.UNDER_REVIEW);
            loan = loanRepository.save(loan);
        } catch (Exception e) {
            log.error("AI risk assessment failed for loan ID: {}", loan.getId(), e);
            loan.setStatus(LoanStatus.UNDER_REVIEW);
            loan.setRiskLevel(RiskLevel.MEDIUM);
            loan.setAiRiskReason("AI assessment unavailable. Manual review required.");
            loan = loanRepository.save(loan);
        }

        redisCacheService.cacheLoanStatus(loan.getId(), loan.getStatus().name());

        // Invalidate all cached pages for this customer
        redisCacheService.deleteByPattern("customer_loans:" + customerId + ":*");

        log.info("Loan application submitted with ID: {}", loan.getId());
        return mapToResponse(loan);
    }

    /**
     * Get loan by ID - with authorization check.
     */
    public LoanApplicationResponse getLoanById(Long loanId, Long currentUserId, String currentUserRole) {
        log.info("Fetching loan ID: {} for user ID: {}", loanId, currentUserId);

        LoanApplication loan = loanRepository.findByIdWithUsers(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan Application", "id", loanId));

        if ("CUSTOMER".equals(currentUserRole) && !loan.getCustomer().getId().equals(currentUserId)) {
            throw UnauthorizedException.forbidden("You can only view your own loan applications");
        }

        return mapToResponse(loan);
    }

    /**
     * Get all loans paginated - OFFICER and ADMIN only.
     * Two-query approach: paginate IDs first, then fetch full entities.
     */
    public Page<LoanApplicationResponse> getAllLoans(Pageable pageable, LoanStatus status) {
        log.info("Fetching all loans with status filter: {}", status);

        Page<Long> idPage = (status != null)
                ? loanRepository.findIdsByStatus(status, pageable)
                : loanRepository.findAllIds(pageable);

        if (idPage.isEmpty()) {
            return Page.empty(pageable);
        }

        List<LoanApplication> loans = loanRepository.findAllWithUsersByIds(idPage.getContent());
        List<LoanApplicationResponse> responses = loans.stream()
                .map(this::mapToResponse)
                .toList();

        return new PageImpl<>(responses, pageable, idPage.getTotalElements());
    }

    /**
     * Get paginated loans for a specific customer - CUSTOMER only.
     *
     * Cache strategy: cache List<LoanApplicationResponse> not Page<T>.
     * Page is reconstructed after cache hit using the cached list + total count.
     * This avoids generic type deserialization issues with Redis.
     */
    public Page<LoanApplicationResponse> getCustomerLoans(Long customerId, Pageable pageable) {
        log.info("Fetching loans for customer ID: {} page: {}", customerId, pageable.getPageNumber());

        String listCacheKey = "customer_loans:" + customerId
                + ":page:" + pageable.getPageNumber()
                + ":size:" + pageable.getPageSize();

        String totalCacheKey = "customer_loans:" + customerId + ":total";

        // Try cache first
        List<LoanApplicationResponse> cachedList = redisCacheService.get(listCacheKey);
        Object rawTotal = redisCacheService.get(totalCacheKey);
        Long cachedTotal = rawTotal != null ? ((Number) rawTotal).longValue() : null;

        if (cachedList != null && cachedTotal != null) {
            log.info("Cache hit for key: {}", listCacheKey);
            return new PageImpl<>(cachedList, pageable, cachedTotal);
        }

        // Cache miss — query DB using two-query approach
        Page<Long> idPage = loanRepository.findIdsByCustomerId(customerId, pageable);

        if (idPage.isEmpty()) {
            return Page.empty(pageable);
        }

        List<LoanApplication> loans = loanRepository.findAllWithUsersByIds(idPage.getContent());
        List<LoanApplicationResponse> responses = loans.stream()
                .map(this::mapToResponse)
                .toList();

        // Cache list and total separately — both are simple types Redis handles safely
        redisCacheService.set(listCacheKey, responses, 5);
        redisCacheService.set(totalCacheKey, idPage.getTotalElements(), 5);

        return new PageImpl<>(responses, pageable, idPage.getTotalElements());
    }

    /**
     * Approve loan application - LOAN_OFFICER only.
     */
    @Transactional
    public LoanApplicationResponse approveLoan(Long loanId, Long officerId, String notes) {
        log.info("Approving loan ID: {} by officer ID: {}", loanId, officerId);

        LoanApplication loan = loanRepository.findByIdWithUsers(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan Application", "id", loanId));

        if (loan.getStatus() == LoanStatus.APPROVED || loan.getStatus() == LoanStatus.REJECTED) {
            throw new BadRequestException("Loan is already " + loan.getStatus().name());
        }

        User officer = userRepository.findById(officerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", officerId));

        loan.setStatus(LoanStatus.APPROVED);
        loan.setOfficer(officer);
        loan.setOfficerNotes(notes);
        loan.setReviewedAt(LocalDateTime.now());
        loan = loanRepository.save(loan);

        redisCacheService.cacheLoanStatus(loan.getId(), loan.getStatus().name());
        redisCacheService.deleteByPattern("customer_loans:" + loan.getCustomer().getId() + ":*");

        log.info("Loan ID: {} approved successfully", loanId);
        return mapToResponse(loan);
    }

    /**
     * Reject loan application - LOAN_OFFICER only.
     */
    @Transactional
    public LoanApplicationResponse rejectLoan(Long loanId, Long officerId, String notes) {
        log.info("Rejecting loan ID: {} by officer ID: {}", loanId, officerId);

        LoanApplication loan = loanRepository.findByIdWithUsers(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan Application", "id", loanId));

        if (loan.getStatus() == LoanStatus.APPROVED || loan.getStatus() == LoanStatus.REJECTED) {
            throw new BadRequestException("Loan is already " + loan.getStatus().name());
        }

        User officer = userRepository.findById(officerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", officerId));

        loan.setStatus(LoanStatus.REJECTED);
        loan.setOfficer(officer);
        loan.setOfficerNotes(notes);
        loan.setReviewedAt(LocalDateTime.now());
        loan = loanRepository.save(loan);

        redisCacheService.cacheLoanStatus(loan.getId(), loan.getStatus().name());
        redisCacheService.deleteByPattern("customer_loans:" + loan.getCustomer().getId() + ":*");

        log.info("Loan ID: {} rejected successfully", loanId);
        return mapToResponse(loan);
    }

    /**
     * Get loan statistics - for admin dashboard.
     */
    public LoanStatistics getStatistics() {
        long pendingCount = loanRepository.countByStatus(LoanStatus.PENDING);
        long underReviewCount = loanRepository.countByStatus(LoanStatus.UNDER_REVIEW);
        long approvedCount = loanRepository.countByStatus(LoanStatus.APPROVED);
        long rejectedCount = loanRepository.countByStatus(LoanStatus.REJECTED);
        long highRiskCount = loanRepository.countByRiskLevel(RiskLevel.HIGH);

        return LoanStatistics.builder()
                .pendingCount(pendingCount)
                .underReviewCount(underReviewCount)
                .approvedCount(approvedCount)
                .rejectedCount(rejectedCount)
                .highRiskCount(highRiskCount)
                .totalLoans(pendingCount + underReviewCount + approvedCount + rejectedCount)
                .approvalRate(calculateApprovalRate(approvedCount, rejectedCount))
                .build();
    }

    private double calculateApprovalRate(long approved, long rejected) {
        long total = approved + rejected;
        if (total == 0) return 0.0;
        return (double) approved / total * 100;
    }

    private LoanApplicationResponse mapToResponse(LoanApplication loan) {
        return LoanApplicationResponse.builder()
                .id(loan.getId())
                .customerId(loan.getCustomer().getId())
                .customerName(loan.getCustomer().getName())
                .customerEmail(loan.getCustomer().getEmail())
                .loanAmount(loan.getLoanAmount())
                .purpose(loan.getPurpose())
                .monthlyIncome(loan.getMonthlyIncome())
                .employmentType(loan.getEmploymentType())
                .creditScore(loan.getCreditScore())
                .riskLevel(loan.getRiskLevel())
                .aiRiskReason(loan.getAiRiskReason())
                .status(loan.getStatus())
                .officerId(loan.getOfficer() != null ? loan.getOfficer().getId() : null)
                .officerName(loan.getOfficer() != null ? loan.getOfficer().getName() : null)
                .officerNotes(loan.getOfficerNotes())
                .createdAt(loan.getCreatedAt())
                .updatedAt(loan.getUpdatedAt())
                .reviewedAt(loan.getReviewedAt())
                .build();
    }

    @lombok.Getter
    @lombok.Builder
    public static class LoanStatistics {
        private long pendingCount;
        private long underReviewCount;
        private long approvedCount;
        private long rejectedCount;
        private long highRiskCount;
        private long totalLoans;
        private double approvalRate;
    }
}