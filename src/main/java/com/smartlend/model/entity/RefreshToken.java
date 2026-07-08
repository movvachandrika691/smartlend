package com.smartlend.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * RefreshToken entity for JWT refresh token rotation.
 * Stored in database and also cached in Redis for fast lookup.
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
    @Index(name = "idx_refresh_token", columnList = "token", unique = true),
    @Index(name = "idx_refresh_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE refresh_tokens SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 512)
    private String token; // The actual refresh token string

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt; // When this token expires

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "revoked")
    @Builder.Default
    private Boolean revoked = false; // Manually revoked on logout

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * Check if this refresh token has expired.
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Check if this token is valid (not expired, not revoked, not deleted).
     */
    public boolean isValid() {
        return !isExpired() && !Boolean.TRUE.equals(revoked) && deletedAt == null;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
