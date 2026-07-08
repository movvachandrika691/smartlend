package com.smartlend.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for rate limiting on controller methods.
 * Apply to endpoints that need protection from abuse.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * Maximum number of requests in the bucket.
     */
    int capacity() default 10;

    /**
     * Number of tokens added per refill.
     */
    int refillTokens() default 10;

    /**
     * Duration for token refill in minutes.
     */
    int refillDurationMinutes() default 1;
}
