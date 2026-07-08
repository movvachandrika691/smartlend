package com.smartlend.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for user login.
 * Only requires email and password for authentication.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Login request — returns accessToken and refreshToken")
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Pattern(regexp = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
             message = "Email must contain a valid domain")
    @Schema(
            description = "Registered email address",
            example = "krishna@test.com"
    )
    private String email;

    @NotBlank(message = "Password is required")
    @Schema(
            description = "Account password",
            example = "Password@123"
    )
    private String password;
}