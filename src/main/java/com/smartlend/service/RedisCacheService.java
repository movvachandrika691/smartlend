package com.smartlend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Service for Redis caching operations.
 * Handles loan data caching and JWT token blacklisting.
 *
 * Caching strategy:
 * - Cache simple types only (List, Long, String) — not Page<T>
 * - Page<T> is reconstructed in service layer after cache hit
 * - This avoids Jackson generic type deserialization issues with Redis
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String JWT_BLACKLIST_PREFIX = "jwt_blacklist:";
    private static final String LOAN_STATUS_PREFIX = "loan_status:";

    /**
     * Cache any object with TTL in minutes.
     */
    public <T> void set(String key, T value, long ttlMinutes) {
        try {
            redisTemplate.opsForValue().set(key, value, ttlMinutes, TimeUnit.MINUTES);
            log.debug("Cached key: {} for {} minutes", key, ttlMinutes);
        } catch (Exception e) {
            log.error("Failed to cache key: {}", key, e);
        }
    }

    /**
     * Get cached object by key.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        try {
            return (T) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("Failed to get cached key: {}", key, e);
            return null;
        }
    }

    /**
     * Delete a single cached key.
     */
    public void delete(String key) {
        try {
            redisTemplate.delete(key);
            log.debug("Deleted cached key: {}", key);
        } catch (Exception e) {
            log.error("Failed to delete cached key: {}", key, e);
        }
    }

    /**
     * Delete all keys matching a pattern.
     * Used to invalidate all pages for a customer when their loan status changes.
     * Example pattern: "customer_loans:18:*"
     */
    public void deleteByPattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("Deleted {} keys matching pattern: {}", keys.size(), pattern);
            }
        } catch (Exception e) {
            log.error("Failed to delete keys by pattern: {}", pattern, e);
        }
    }

    /**
     * Add JWT token to blacklist - used on logout.
     */
    public void blacklistJwtToken(String tokenId, long expirationMillis) {
        String key = JWT_BLACKLIST_PREFIX + tokenId;
        long ttlSeconds = expirationMillis / 1000;
        redisTemplate.opsForValue().set(key, "blacklisted", ttlSeconds, TimeUnit.SECONDS);
        log.info("Token blacklisted: {}", tokenId);
    }

    /**
     * Check if JWT token is blacklisted.
     */
    public boolean isJwtBlacklisted(String tokenId) {
        String key = JWT_BLACKLIST_PREFIX + tokenId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Cache loan status for quick lookup.
     */
    public void cacheLoanStatus(Long loanId, String status) {
        String key = LOAN_STATUS_PREFIX + loanId;
        redisTemplate.opsForValue().set(key, status, 30, TimeUnit.MINUTES);
    }

    /**
     * Get cached loan status.
     */
    public String getCachedLoanStatus(Long loanId) {
        String key = LOAN_STATUS_PREFIX + loanId;
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Increment rate limit counter for a key.
     */
    public Long incrementRateLimit(String key, long ttlSeconds) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
        }
        return count;
    }

    /**
     * Get current rate limit count.
     */
    public Long getRateLimitCount(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) return 0L;
        return Long.parseLong(value.toString());
    }

    /**
     * Clear all cached loan data (admin operation).
     */
    public void clearLoanCache() {
        Set<String> keys = redisTemplate.keys(LOAN_STATUS_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        log.info("Cleared all loan status cache");
    }
}