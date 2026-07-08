package com.smartlend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
/**
 * Additional OpenAPI annotations for security scheme definition.
 */
@Configuration
@OpenAPIDefinition
@SecurityScheme(
        name = "Refresh Token",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "Refresh-Token",
        description = """
                Paste **only** the `refreshToken` from the login response.

                Example:
                ```
                eyJhbGciOiJQUzI1NiJ9.eyJzdWIiOiIxIn0...
                ```

                Token is valid for **7 days**.
                """
)
public class OpenApiConfig {
    // Configuration is done via annotations for security scheme
}
