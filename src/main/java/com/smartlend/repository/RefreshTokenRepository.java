package com.smartlend.repository;

import com.smartlend.model.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for RefreshToken entity operations.
 * Handles JWT refresh token persistence and cleanup.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Find refresh token by token string - used for token validation.
     */
    Optional<RefreshToken> findByToken(String token);

    /**
     * Find all refresh tokens for a user - used for logout (revoke all).
     */
    List<RefreshToken> findByUserId(Long userId);

    /**
     * Check if token exists - quick validation check.
     */
    boolean existsByToken(String token);

    /**
     * Revoke all refresh tokens for a user - used on logout all sessions.
     */
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.user.id = :userId")
    void revokeAllByUserId(Long userId);

    /**
     * Delete expired tokens - scheduled cleanup job.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :now")
    void deleteExpiredTokens(LocalDateTime now);

    /**
     * Find valid (non-expired, non-revoked) tokens for user.
     */
    @Query("SELECT r FROM RefreshToken r WHERE r.user.id = :userId AND r.revoked = false AND r.expiresAt > :now")
    List<RefreshToken> findValidTokensByUserId(Long userId, LocalDateTime now);
}
