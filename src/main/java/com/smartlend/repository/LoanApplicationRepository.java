package com.smartlend.repository;

import com.smartlend.model.entity.LoanApplication;
import com.smartlend.model.enums.LoanStatus;
import com.smartlend.model.enums.RiskLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for LoanApplication entity operations.
 *
 * Important: JOIN FETCH cannot be used directly with Pageable —
 * Hibernate falls back to in-memory pagination and loads all rows,
 * which causes EntityNotFoundException for deleted/orphaned records.
 *
 * Solution: paginate by ID first (no JOIN FETCH), then fetch full
 * entities with JOIN FETCH using the resulting ID list.
 * This is the standard two-query approach for safe paginated eager loading.
 */
@Repository
public interface LoanApplicationRepository extends JpaRepository<LoanApplication, Long> {

    /**
     * Find loan by ID with customer and officer loaded (eager fetch).
     */
    @Query("SELECT l FROM LoanApplication l " +
           "LEFT JOIN FETCH l.customer " +
           "LEFT JOIN FETCH l.officer " +
           "WHERE l.id = :id")
    Optional<LoanApplication> findByIdWithUsers(@Param("id") Long id);

    /**
     * Find all loans by status - for officer dashboard.
     */
    Page<LoanApplication> findByStatus(LoanStatus status, Pageable pageable);

    /**
     * Find loans by risk level - for risk analysis.
     */
    List<LoanApplication> findByRiskLevel(RiskLevel riskLevel);

    /**
     * Find loans assigned to a specific officer.
     */
    List<LoanApplication> findByOfficerId(Long officerId);

    /**
     * Count loans by status - for dashboard statistics.
     */
    long countByStatus(LoanStatus status);

    /**
     * Count loans by risk level - for risk metrics.
     */
    long countByRiskLevel(RiskLevel riskLevel);

    /**
     * Calculate total loan amount by status.
     */
    @Query("SELECT SUM(l.loanAmount) FROM LoanApplication l WHERE l.status = :status")
    BigDecimal sumLoanAmountByStatus(@Param("status") LoanStatus status);

    /**
     * Find loans created between dates - for reporting.
     */
    @Query("SELECT l FROM LoanApplication l WHERE l.createdAt BETWEEN :start AND :end")
    List<LoanApplication> findByCreatedAtBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * Get average loan amount.
     */
    @Query("SELECT AVG(l.loanAmount) FROM LoanApplication l")
    BigDecimal findAverageLoanAmount();

    /**
     * Count loans by status for a specific customer.
     */
    long countByCustomerIdAndStatus(Long customerId, LoanStatus status);

    // ─────────────────────────────────────────────────────────────
    // Two-query approach for safe paginated eager loading
    // Step 1: paginate IDs only (no JOIN FETCH — database does pagination)
    // Step 2: fetch full entities by IDs with JOIN FETCH (no pagination)
    // ─────────────────────────────────────────────────────────────

    /**
     * Step 1 — Get paginated IDs for all loans (no JOIN FETCH).
     */
    @Query("SELECT l.id FROM LoanApplication l ORDER BY l.createdAt DESC")
    Page<Long> findAllIds(Pageable pageable);

    /**
     * Step 1 — Get paginated IDs filtered by status (no JOIN FETCH).
     */
    @Query("SELECT l.id FROM LoanApplication l WHERE l.status = :status ORDER BY l.createdAt DESC")
    Page<Long> findIdsByStatus(@Param("status") LoanStatus status, Pageable pageable);

    /**
     * Step 1 — Get paginated IDs for a specific customer (no JOIN FETCH).
     */
    @Query("SELECT l.id FROM LoanApplication l WHERE l.customer.id = :customerId ORDER BY l.createdAt DESC")
    Page<Long> findIdsByCustomerId(@Param("customerId") Long customerId, Pageable pageable);

    /**
     * Step 2 — Fetch full loan entities with eager loading by ID list.
     * Used after pagination is done in Step 1.
     */
    @Query("SELECT l FROM LoanApplication l " +
           "LEFT JOIN FETCH l.customer " +
           "LEFT JOIN FETCH l.officer " +
           "WHERE l.id IN :ids " +
           "ORDER BY l.createdAt DESC")
    List<LoanApplication> findAllWithUsersByIds(@Param("ids") List<Long> ids);
}