package com.smartlend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import org.springframework.context.annotation.Configuration;

/**
 * Defines security schemes via annotations.
 */
@Configuration
@OpenAPIDefinition
@SecuritySchemes({
        @SecurityScheme(
                name = "Bearer Authentication",
                type = SecuritySchemeType.HTTP,
                scheme = "bearer",
                bearerFormat = "JWT",
                description = "Paste only the accessToken from login. Do not include 'Bearer'. Valid for 24 hrs."
        ),
        @SecurityScheme(
                name = "Refresh Token",
                type = SecuritySchemeType.APIKEY,
                in = SecuritySchemeIn.HEADER,
                paramName = "Refresh-Token",
                description = "Paste only the refreshToken from login. Do not include 'Bearer'. Valid for 7 days."
        )
})
public class OpenApiConfig {
}