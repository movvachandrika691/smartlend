package com.smartlend.service;

import com.smartlend.exception.UnauthorizedException;
import com.smartlend.model.dto.AuthResponse;
import com.smartlend.model.dto.LoginRequest;
import com.smartlend.model.dto.RegisterRequest;
import com.smartlend.model.entity.RefreshToken;
import com.smartlend.model.entity.User;
import com.smartlend.model.enums.Role;
import com.smartlend.repository.RefreshTokenRepository;
import com.smartlend.repository.UserRepository;
import com.smartlend.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service layer for authentication and token management.
 * Handles login, register, refresh token, and logout operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserService userService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    /**
     * Register new user and return tokens.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Processing registration for email: {}", request.getEmail());

        // Register user via UserService
        User user = userService.registerUser(
                request.getName(),
                request.getEmail(),
                request.getPassword(),
                Role.CUSTOMER
        );

        // Generate tokens
        String accessToken = jwtService.generateToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        // Save refresh token to database
        saveRefreshToken(user, refreshToken);

        log.info("Registration successful for user ID: {}", user.getId());

        return AuthResponse.of(
                accessToken,
                refreshToken,
                jwtExpiration / 1000,
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }

    /**
     * Authenticate user and return tokens.
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        // Authenticate via Spring Security
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        // Get authenticated user
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        // Generate tokens
        String accessToken = jwtService.generateToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        // Save refresh token to database
        saveRefreshToken(user, refreshToken);

        log.info("Login successful for user ID: {}", user.getId());

        return AuthResponse.of(
                accessToken,
                refreshToken,
                jwtExpiration / 1000,
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }

    /**
     * Refresh access token using refresh token.
     */
    @Transactional
    public AuthResponse refreshToken(String refreshToken) {
        log.info("Processing token refresh");

        // Validate refresh token
        RefreshToken storedToken = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (!jwtService.validateToken(refreshToken)) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        if (!storedToken.isValid()) {
            throw new UnauthorizedException("Refresh token expired or revoked");
        }

        // Get user
        User user = storedToken.getUser();

        // Revoke old refresh token (token rotation for security)
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        // Generate new tokens
        String newAccessToken = jwtService.generateToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user);

        // Save new refresh token
        saveRefreshToken(user, newRefreshToken);

        log.info("Token refresh successful for user ID: {}", user.getId());

        return AuthResponse.of(
                newAccessToken,
                newRefreshToken,
                jwtExpiration / 1000,
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }

    /**
     * Logout user - revoke refresh token and blacklist access token.
     * Note: blacklist check is already handled inside jwtService.validateToken()
     */
    @Transactional
    public void logout(String refreshToken, String accessToken) {
        log.info("Processing logout");

        // Validate access token presence
        if (accessToken == null || accessToken.isBlank()) {
            throw new UnauthorizedException("Missing or invalid access token");
        }

        // Validate access token - this also checks blacklist internally
        if (!jwtService.validateToken(accessToken)) {
            throw new UnauthorizedException("Invalid or expired token");
        }

        // Validate refresh token presence
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new UnauthorizedException("Missing refresh token");
        }

        // Find and validate stored refresh token
        RefreshToken storedRefreshToken = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (!storedRefreshToken.isValid()) {
            throw new UnauthorizedException("Refresh token expired or revoked");
        }

        // Revoke refresh token
        storedRefreshToken.setRevoked(true);
        refreshTokenRepository.save(storedRefreshToken);

        // Blacklist access token
        jwtService.blacklistToken(accessToken);

        log.info("Logout successful for user: {}", storedRefreshToken.getUser().getEmail());
    }

    /**
     * Logout from all devices - revoke all refresh tokens for user.
     */
    @Transactional
    public void logoutAll(Long userId) {
        log.info("Processing logout all sessions for user ID: {}", userId);
        refreshTokenRepository.revokeAllByUserId(userId);
        log.info("All sessions revoked for user ID: {}", userId);
    }

    /**
     * Save refresh token to database.
     */
    private void saveRefreshToken(User user, String token) {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(token)
                .expiresAt(LocalDateTime.now().plusNanos(refreshExpiration * 1_000_000))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);
    }
}