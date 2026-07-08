package com.smartlend.repository;

import com.smartlend.model.entity.User;
import com.smartlend.model.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for User entity operations.
 * Spring Data JPA auto-generates SQL from method names.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by email - used for login authentication.
     * Spring generates: SELECT * FROM users WHERE email = ? AND deleted_at IS NULL
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if email exists - used for registration validation.
     */
    boolean existsByEmail(String email);

    /**
     * Find all users by role - used for admin dashboards.
     */
    List<User> findByRole(Role role);

    /**
     * Find users created within a date range.
     */
    @Query("SELECT u FROM User u WHERE u.createdAt BETWEEN :startDate AND :endDate")
    List<User> findByCreatedAtBetween(java.time.LocalDateTime startDate, java.time.LocalDateTime endDate);

    /**
     * Count users by role - for admin statistics.
     */
    long countByRole(Role role);
}
