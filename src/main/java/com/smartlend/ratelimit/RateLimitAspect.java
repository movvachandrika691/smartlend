package com.smartlend.ratelimit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlend.exception.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitAspect {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Around("@annotation(rateLimit)")
    public Object enforceRateLimit(
            ProceedingJoinPoint joinPoint,
            RateLimit rateLimit) throws Throwable {

        String key = getRateLimitKey(joinPoint);

        log.debug("Rate limit key generated: {}", key);

        Bucket bucket = buckets.computeIfAbsent(
                key,
                k -> createBucket(rateLimit)
        );

        if (bucket.tryConsume(1)) {
            log.debug("Rate limit passed for {}", key);
            return joinPoint.proceed();
        }

        log.warn("Rate limit exceeded for {}", key);

        throw new RateLimitExceededException(
                "Too many requests. Please try again after one minute."
        );
    }

    private Bucket createBucket(RateLimit rateLimit) {

        Refill refill = Refill.intervally(
                rateLimit.refillTokens(),
                Duration.ofMinutes(rateLimit.refillDurationMinutes())
        );

        Bandwidth bandwidth = Bandwidth.classic(
                rateLimit.capacity(),
                refill
        );

        return Bucket.builder()
                .addLimit(bandwidth)
                .build();
    }

    /**
     * Creates a unique bucket key.
     *
     * Login/Register:
     *      email + endpoint
     *
     * Authenticated APIs:
     *      authenticated username/email + endpoint
     *
     * Anonymous:
     *      IP + endpoint
     */
    private String getRateLimitKey(ProceedingJoinPoint joinPoint) {


        String endpoint = getEndpointPath();

        // Login & Register -> use email from request body
        if (endpoint.equals("/api/auth/login") || endpoint.equals("/api/auth/register")) {

            for (Object arg : joinPoint.getArgs()) {

                try {
                    JsonNode json = objectMapper.valueToTree(arg);

                    if (json.has("email")) {
                        return "email:"
                                + json.get("email").asText().toLowerCase()
                                + ":" + endpoint;
                    }

                } catch (Exception ignored) {
                }
            }
        }

        // Authenticated APIs
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {

            return "user:"
                    + authentication.getName()
                    + ":" + endpoint;
        }

        // Anonymous fallback
        return "ip:"
                + getClientIpAddress()
                + ":" + endpoint;
    }

    private String getClientIpAddress() {

        ServletRequestAttributes attributes =
                (ServletRequestAttributes)
                        RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            return "unknown";
        }

        HttpServletRequest request = attributes.getRequest();

        String forwarded = request.getHeader("X-Forwarded-For");

        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }

    private String getEndpointPath() {

        ServletRequestAttributes attributes =
                (ServletRequestAttributes)
                        RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            return "unknown";
        }

        return attributes.getRequest().getRequestURI();
    }
}