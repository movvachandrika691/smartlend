package com.smartlend.model.entity;

import com.smartlend.model.enums.LoanStatus;
import com.smartlend.model.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * LoanApplication entity for customer loan requests.
 * Contains financial details and AI-generated risk assessment.
 */
@Entity
@Table(name = "loan_applications", indexes = {
    @Index(name = "idx_loan_customer", columnList = "customer_id"),
    @Index(name = "idx_loan_status", columnList = "status"),
    @Index(name = "idx_loan_officer", columnList = "officer_id"),
    @Index(name = "idx_loan_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE loan_applications SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
public class LoanApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Customer who submitted the application
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Column(name = "loan_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal loanAmount;

    @Column(columnDefinition = "TEXT")
    private String purpose; // Reason for loan request

    @Column(name = "monthly_income", nullable = false, precision = 15, scale = 2)
    private BigDecimal monthlyIncome;

    @Column(name = "employment_type", nullable = false)
    private String employmentType; // SALARIED, SELF_EMPLOYED, BUSINESS

    @Column(name = "credit_score")
    private Integer creditScore; // Customer's credit score (300-900)

    // AI-generated assessment fields
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level")
    private RiskLevel riskLevel; // LOW, MEDIUM, HIGH from AI

    @Column(name = "ai_risk_reason", columnDefinition = "TEXT")
    private String aiRiskReason; // Explanation from OpenAI

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private LoanStatus status = LoanStatus.PENDING;

    @Column(name = "officer_notes", columnDefinition = "TEXT")
    private String officerNotes; // Notes from loan officer

    // Loan officer who reviewed the application
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "officer_id")
    private User officer; // Assigned after review starts

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt; // When officer made decision

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = LoanStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
