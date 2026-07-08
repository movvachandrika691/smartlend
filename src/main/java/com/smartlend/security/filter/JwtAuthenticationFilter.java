package com.smartlend.security.filter;

import com.smartlend.security.jwt.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        /*
         * No Authorization header.
         * Leave the request unauthenticated.
         * Spring Security will invoke AuthenticationEntryPoint if required.
         */
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(7);

        try {

            if (!jwtService.validateToken(jwt)) {
                request.setAttribute("jwt_error", "invalid");
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            Long userId = jwtService.extractUserId(jwt);
            String email = jwtService.extractEmail(jwt);
            String role = jwtService.extractRole(jwt);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId,
                            email,
                            Collections.singletonList(
                                    new SimpleGrantedAuthority("ROLE_" + role)
                            )
                    );

            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

        }
        catch (ExpiredJwtException ex) {

            log.warn("JWT expired");

            request.setAttribute("jwt_error", "expired");

            SecurityContextHolder.clearContext();

        }
        catch (JwtException ex) {

            log.warn("Invalid JWT: {}", ex.getMessage());

            request.setAttribute("jwt_error", "invalid");

            SecurityContextHolder.clearContext();

        }
        catch (Exception ex) {

            log.error("JWT authentication failed", ex);

            request.setAttribute("jwt_error", "invalid");

            SecurityContextHolder.clearContext();

        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return true;
    }
}