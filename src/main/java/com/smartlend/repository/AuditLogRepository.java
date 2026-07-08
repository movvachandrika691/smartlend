package com.smartlend.repository;

import com.smartlend.model.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository interface for AuditLog entity operations.
 * Read-only for application logic - logs are written by AOP aspect.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Find all logs for a specific user - user activity history.
     */
    Page<AuditLog> findByUserId(Long userId, Pageable pageable);

    /**
     * Find logs by action type - e.g., all loan approvals.
     */
    List<AuditLog> findByAction(String action);

    /**
     * Find logs for a specific entity - entity history.
     */
    List<AuditLog> findByEntityTypeAndEntityId(String entityType, Long entityId);

    /**
     * Find logs between timestamps - for date range queries.
     */
    @Query("SELECT a FROM AuditLog a WHERE a.timestamp BETWEEN :start AND :end ORDER BY a.timestamp DESC")
    List<AuditLog> findByTimestampBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * Find all logs with pagination, ordered by timestamp.
     */
    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);

    /**
     * Count actions of a specific type - for statistics.
     */
    long countByAction(String action);

    /**
     * Find recent logs - last N hours.
     */
    @Query("SELECT a FROM AuditLog a WHERE a.timestamp > :since ORDER BY a.timestamp DESC")
    List<AuditLog> findRecentLogs(@Param("since") LocalDateTime since);
}
