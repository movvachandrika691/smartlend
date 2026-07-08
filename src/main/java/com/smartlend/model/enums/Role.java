package com.smartlend.model.enums;

/**
 * User roles for role-based access control (RBAC).
 * ADMIN: Full system access, manage users and view all loans.
 * LOAN_OFFICER: Review and approve/reject loan applications.
 * CUSTOMER: Submit loan applications and view own loans.
 */
public enum Role {
    CUSTOMER,
    LOAN_OFFICER,
    ADMIN
}
