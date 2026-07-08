package com.smartlend.model.enums;

/**
 * Loan application status lifecycle.
 * PENDING: Initial state after customer submission.
 * UNDER_REVIEW: Being evaluated by loan officer.
 * APPROVED: Loan approved, ready for disbursement.
 * REJECTED: Loan application rejected.
 */
public enum LoanStatus {
    PENDING,
    UNDER_REVIEW,
    APPROVED,
    REJECTED
}
