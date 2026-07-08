package com.smartlend.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * AuditLog entity for tracking all system actions.
 * AOP aspect automatically logs every significant operation.
 * Important for compliance in fintech/banking applications.
 */
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_user", columnList = "user_id"),
    @Index(name = "idx_audit_action", columnList = "action"),
    @Index(name = "idx_audit_entity", columnList = "entity_type, entity_id"),
    @Index(name = "idx_audit_timestamp", columnList = "timestamp")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId; // User who performed the action (null for system actions)

    @Column(nullable = false)
    private String action; // CREATE_LOAN, APPROVE_LOAN, REJECT_LOAN, LOGIN, LOGOUT, etc.

    @Column(name = "entity_type")
    private String entityType; // LOAN_APPLICATION, USER, etc.

    @Column(name = "entity_id")
    private Long entityId; // ID of the affected entity

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue; // JSON snapshot before change

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue; // JSON snapshot after change

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    @Column(name = "ip_address")
    private String ipAddress; // Client IP address

    @Column(columnDefinition = "TEXT")
    private String details; // Additional context or error message

    /**
     * Create a quick audit log entry.
     */
    public static AuditLog of(Long userId, String action, String entityType, Long entityId) {
        return AuditLog.builder()
                .userId(userId)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
