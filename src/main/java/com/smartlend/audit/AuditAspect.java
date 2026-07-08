package com.smartlend.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlend.model.entity.AuditLog;
import com.smartlend.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * AOP Aspect for automatic audit logging.
 * Logs all controller method calls with user, action, and changes.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Log all controller method executions.
     */
    @Around("execution(* com.smartlend.controller.*.*(..))")
    public Object logControllerAction(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        // Get request details
        HttpServletRequest request = getCurrentRequest();
        String action = joinPoint.getSignature().getName();
        String entityType = extractEntityType(joinPoint);

        // Get current user ID from security context
        Long userId = getCurrentUserId();
        String ipAddress = request != null ? getClientIpAddress(request) : "unknown";

        Object result = null;
        Throwable error = null;
        String oldValue = null;
        String newValue = null;

        try {
            // Execute the method
            result = joinPoint.proceed();

            // Capture result as new value
            newValue = serializeObject(result);

            log.info("Action {} completed in {}ms by user {}", action,
                    System.currentTimeMillis() - startTime, userId);

        } catch (Throwable e) {
            error = e;
            log.error("Action {} failed for user {}: {}", action, userId, e.getMessage());
            throw e;
        } finally {
            // Save audit log
            try {
                AuditLog auditLog = AuditLog.builder()
                        .userId(userId)
                        .action(action)
                        .entityType(entityType)
                        .oldValue(oldValue)
                        .newValue(newValue)
                        .ipAddress(ipAddress)
                        .timestamp(LocalDateTime.now())
                        .details(error != null ? error.getMessage() : null)
                        .build();

                auditLogRepository.save(auditLog);
                log.debug("Audit log saved for action: {}", action);
            } catch (Exception e) {
                log.error("Failed to save audit log: {}", e.getMessage());
            }
        }

        return result;
    }

    /**
     * Get current HTTP request from context.
     */
    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    /**
     * Get current user ID from Spring Security context.
     */
    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long) {
            return (Long) auth.getPrincipal();
        }
        return null; // System action or unauthenticated
    }

    /**
     * Extract entity type from method signature.
     */
    private String extractEntityType(ProceedingJoinPoint joinPoint) {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        if (className.contains("Loan")) return "LOAN_APPLICATION";
        if (className.contains("Auth")) return "USER";
        if (className.contains("Admin")) return "AUDIT_LOG";
        return className.replace("Controller", "").toUpperCase();
    }

    /**
     * Get client IP address from request.
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Serialize object to JSON string for storage.
     */
    private String serializeObject(Object obj) {
        try {
            if (obj == null) return null;
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "Failed to serialize: " + e.getMessage();
        }
    }
}
