package com.smartlend.model.dto;

import com.smartlend.model.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for authentication endpoints (login, register, refresh).
 * Contains JWT tokens and user profile information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String accessToken; // Short-lived JWT (24 hours)
    private String refreshToken; // Long-lived token (7 days)
    private String tokenType; // Always "Bearer"
    private Long expiresIn; // Access token expiry in seconds
    private UserInfo user;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private Long id;
        private String name;
        private String email;
        private Role role;
    }

    /**
     * Quick builder for auth response.
     */
    public static AuthResponse of(String accessToken, String refreshToken, Long expiresIn, Long userId, String name, String email, Role role) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .user(UserInfo.builder()
                        .id(userId)
                        .name(name)
                        .email(email)
                        .role(role)
                        .build())
                .build();
    }
}
