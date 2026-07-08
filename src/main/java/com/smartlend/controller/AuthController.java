package com.smartlend.controller;

import com.smartlend.model.dto.ApiResponse;
import com.smartlend.model.dto.AuthResponse;
import com.smartlend.model.dto.LoginRequest;
import com.smartlend.model.dto.RegisterRequest;
import com.smartlend.service.AuthService;
import com.smartlend.ratelimit.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication endpoints — register, login, refresh, logout.
 * Public endpoints (register, login, refresh) do not require a token.
 * Logout requires both Authorization and Refresh-Token headers.
 */
@RestController
@RequestMapping(value = "/auth", produces = "application/json")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, Login, Token Refresh, and Logout")
public class AuthController {

    private final AuthService authService;

    // ─────────────────────────────────────────────
    // Register
    // ─────────────────────────────────────────────

    @PostMapping("/register")
    @RateLimit(capacity = 5, refillTokens = 5, refillDurationMinutes = 1)
    @Operation(
            operationId = "01_register",
            summary = "Register",
            description = """
                    Create a new **CUSTOMER** account.

                    Returns an `accessToken` (15 min) and a `refreshToken` (7 days).

                    > Rate limited to **5 requests per minute** per IP.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Registration successful — tokens returned"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Validation failed or email already registered",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "429",
                    description = "Too many requests — rate limit exceeded",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "User registered successfully",
                        authService.register(request)
                )
        );
    }

    // ─────────────────────────────────────────────
    // Login
    // ─────────────────────────────────────────────

    @PostMapping("/login")
    @RateLimit(capacity = 5, refillTokens = 5, refillDurationMinutes = 1)
    @Operation(
            operationId = "02_login",
            summary = "Login",
            description = """
                    Authenticate with email and password.

                    Returns an `accessToken` (15 min) and a `refreshToken` (7 days).

                    Use the `accessToken` in the **Authorize** button at the top of this page.

                    > Rate limited to **5 requests per minute** per IP.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Login successful — tokens returned"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Invalid email or password",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "429",
                    description = "Too many requests — rate limit exceeded",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Login successful",
                        authService.login(request)
                )
        );
    }

    // ─────────────────────────────────────────────
    // Refresh Token
    // ─────────────────────────────────────────────

    @PostMapping("/refresh")
    @SecurityRequirement(name = "Refresh Token")
    @Operation(
            operationId = "03_refresh",
            summary = "Refresh Access Token",
            description = """
                    Exchange a valid **Refresh Token** for a new token pair.

                    - New `accessToken` (15 min)
                    - New `refreshToken` (7 days)

                    The previous refresh token is **immediately revoked** (token rotation).

                    Pass the refresh token in the `Refresh-Token` header.
                    """
    )
    @Parameter(
            name = "Refresh-Token",
            in = ParameterIn.HEADER,
            required = true,
            description = "Your current valid refresh token",
            schema = @Schema(type = "string", example = "eyJhbGciOiJQUzI1NiJ9...")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "New token pair returned"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Refresh token is invalid, expired, or already revoked",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @RequestHeader("Refresh-Token") String refreshToken) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Token refreshed successfully",
                        authService.refreshToken(refreshToken)
                )
        );
    }

    // ─────────────────────────────────────────────
    // Logout
    // ─────────────────────────────────────────────

    @PostMapping("/logout")
    @SecurityRequirement(name = "Bearer Authentication")
    @SecurityRequirement(name = "Refresh Token")
    @Operation(
            operationId = "04_logout",
            summary = "Logout",
            description = """
                    Logout the current session.

                    **Required headers:**

                    | Header | Value |
                    |--------|-------|
                    | `Authorization` | `Bearer <accessToken>` |
                    | `Refresh-Token` | `<refreshToken>` |

                    **What happens on logout:**
                    - Refresh token is permanently revoked in the database
                    - Access token is blacklisted in Redis until it expires naturally

                    Calling any secured endpoint after logout will return **401**.
                    """
    )
    @Parameter(
            name = "Authorization",
            in = ParameterIn.HEADER,
            required = true,
            description = "Bearer access token",
            schema = @Schema(type = "string", example = "Bearer eyJhbGciOiJQUzI1NiJ9...")
    )
    @Parameter(
            name = "Refresh-Token",
            in = ParameterIn.HEADER,
            required = true,
            description = "Refresh token to revoke",
            schema = @Schema(type = "string", example = "eyJhbGciOiJQUzI1NiJ9...")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Logout successful"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized — Invalid tokens",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("Authorization") String accessToken,
            @RequestHeader("Refresh-Token") String refreshToken) {

        authService.logout(accessToken, refreshToken);
        return ResponseEntity.ok(
                ApiResponse.success("Logged out successfully", null)
        );
    }
}
