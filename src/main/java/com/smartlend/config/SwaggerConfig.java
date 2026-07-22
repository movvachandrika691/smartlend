package com.smartlend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import org.springdoc.core.customizers.OpenApiCustomizer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

@Configuration
public class SwaggerConfig {

    @Value("${server.port:8080}")
    private String serverPort;

   // Single consistent security scheme name used everywhere
    public static final String SECURITY_SCHEME = "Bearer Authentication";

    @Bean
    public OpenAPI smartLendOpenAPI() {

        return new OpenAPI()

                .info(new Info()
                        .title("SmartLend API")
                        .version("1.0.0")
                        .description("""
                                ## AI-Powered Loan Risk Assessment System
 
                                SmartLend automates loan application processing using AI-driven \
                                risk scoring, JWT authentication, Redis caching, and role-based access control.
 
                                ---
 
                                <details>
                                <summary><strong>🚀 How to Get Started</strong> — Register, login and authorize to test all APIs</summary>
 
                                <br>
 
                                **Step 1 — Register**
                                Use **POST /auth/register** to create a CUSTOMER account.
 
                                **Step 2 — Login**
                                Call **POST /auth/login** with your email and password.
 
                                **Step 3 — Copy Tokens**
                                The login response returns two tokens:
                                - `accessToken` — valid for 24 hours, used to authorize API calls
                                - `refreshToken` — valid for 7 days, used only to get a new access token
 
                                **Step 4 — Authorize**
                                Click the **Authorize** button at the top right of this page.
                                Paste **only the accessToken** — Swagger automatically adds "Bearer".
 
                                **Step 5 — Submit a Loan**
                                Call **POST /loans** with your financial details.
                                AI risk assessment runs automatically after submission.
 
                                **Step 6 — View Your Loans**
                                Call **GET /loans/my** to see your applications and their status.
 
                                **Step 7 — Officer Review**
                                Login as a LOAN_OFFICER using the test credentials below.
                                Call **PATCH /loans/{id}/approve** or **PATCH /loans/{id}/reject**.
 
                                **Step 8 — AI Chat**
                                Call **POST /ai/chat** and ask anything about loan eligibility.
 
                                **Step 9 — Admin Dashboard**
                                Login as ADMIN and call **GET /admin/stats** to see loan statistics,
                                or **GET /admin/audit-logs** to see all system activity.
 
                                **Step 10 — Logout**
                                Call **POST /auth/logout** with both Authorization and Refresh-Token headers.
                                Your tokens are immediately invalidated.
 
                                </details>
 
                                ---
 
                                <details>
                                <summary><strong>🔑 Test Credentials</strong> — Pre-created accounts for each role</summary>
 
                                <br>
 
                                | Role | Email | Password |
                                |------|-------|----------|
                                | `CUSTOMER` | customer@test.com | Test@1234 |
                                | `LOAN_OFFICER` | officer@test.com | Test@1234 |
                                | `ADMIN` | admin@test.com | Test@1234 |
 
                                > LOAN_OFFICER and ADMIN accounts cannot be created via the register API.
                                > Use the credentials above to test those roles.
 
                                </details>
 
                                ---
 
                                <details>
                                <summary><strong>👥 User Roles</strong> — What each role can access</summary>
 
                                <br>
 
                                | Role | Access |
                                |------|--------|
                                | `CUSTOMER` | Register, Login, Submit Loans, View Own Loans, AI Chat |
                                | `LOAN_OFFICER` | View All Loans, Approve Loans, Reject Loans, AI Chat |
                                | `ADMIN` | Dashboard Statistics, Audit Logs, AI Chat |
 
                                </details>
 
                                ---
 
                                ### Key Features
 
                                - **RSA JWT Authentication** — access token (24 hrs) + refresh token (7 days) with rotation
                                - **AI Risk Scoring** — automatic LOW / MEDIUM / HIGH risk assessment on loan submission
                                - **Redis Caching** — loan data cached, JWT tokens blacklisted on logout
                                - **Rate Limiting** — register, login, loan submission and AI chat are rate limited
                                - **Audit Logging** — all write operations automatically logged via AOP
                                """)
                        .contact(new Contact()
                                .name("Chandrika Movva")
                                .email("movvachandrika691@gmail.com"))
                )

                // security requirement — matches controllers
                .addSecurityItem(
                        new SecurityRequirement().addList(SECURITY_SCHEME)
                )

                .components(new Components()
                        .addSecuritySchemes(
                                SECURITY_SCHEME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("""
                                                Paste **only** the `accessToken` from the login response.

                                                Do NOT include the word "Bearer" — Swagger adds it automatically.

                                                Example:
                                                ```
                                                eyJhbGciOiJQUzI1NiJ9.eyJzdWIiOiIxIn0...
                                                ```
        
                                                """)
                        )
                )

                .tags(List.of(
                        new Tag()
                                .name("Authentication")
                                .description("Register, Login, Token Refresh, and Logout"),
                        new Tag()
                                .name("Loan Management")
                                .description("Submit loans, view applications, approve or reject"),
                        new Tag()
                                .name("AI Features")
                                .description("AI-powered loan risk chat assistant"),
                        new Tag()
                                .name("Admin")
                                .description("Dashboard statistics and audit log access — ADMIN only")
                ));
    }

    @Bean
    public OpenApiCustomizer sortOperationsByIdCustomizer() {
        return openApi -> {
            Paths paths = openApi.getPaths();
            if (paths == null) return;
          // LinkedHashMap locks and preserves insertion order
            Map<String, PathItem> sortedMap = new LinkedHashMap<>();

            paths.entrySet().stream()
                    .sorted((entry1, entry2) -> {
                        String id1 = getOperationId(entry1.getValue());
                        String id2 = getOperationId(entry2.getValue());
                        return id1.compareTo(id2); // Alphabetically sorts by "01_submitLoan", "02_..."
                    })
                    .forEach(entry -> sortedMap.put(entry.getKey(), entry.getValue()));

            Paths sortedPaths = new Paths();
            sortedPaths.putAll(sortedMap);
            openApi.setPaths(sortedPaths);
        };
    }

    private String getOperationId(PathItem pathItem) {
        if (pathItem.getGet() != null && pathItem.getGet().getOperationId() != null) return pathItem.getGet().getOperationId();
        if (pathItem.getPost() != null && pathItem.getPost().getOperationId() != null) return pathItem.getPost().getOperationId();
        if (pathItem.getPut() != null && pathItem.getPut().getOperationId() != null) return pathItem.getPut().getOperationId();
        if (pathItem.getDelete() != null && pathItem.getDelete().getOperationId() != null) return pathItem.getDelete().getOperationId();
        if (pathItem.getPatch() != null && pathItem.getPatch().getOperationId() != null) return pathItem.getPatch().getOperationId();
        return "99_unknown"; // Fallback for endpoints missing an explicit operationId
    }
}