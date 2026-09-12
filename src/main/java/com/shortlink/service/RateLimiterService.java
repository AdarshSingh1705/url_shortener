package com.shortlink.service;

import com.shortlink.exception.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Fixed-window rate limiter: each client gets N requests per rolling-but-aligned
 * window (e.g. per calendar minute), tracked with a single Redis INCR + EXPIRE.
 * <p>
 * This is intentionally the simplest correct algorithm, not a token bucket or
 * sliding-window log — it's O(1) per request and needs no background job, at the
 * cost of allowing a burst of up to 2x the limit right at a window boundary
 * (e.g. a client could send N requests at 0:59 and another N at 1:00). For this
 * project's purposes (protecting the shorten endpoint from abuse, not billing-grade
 * accuracy) that trade-off is fine; a sliding-window or token-bucket algorithm would
 * be the next step if stricter guarantees were needed.
 */
@Service
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;
    private final int limit;
    private final Duration window;

    public RateLimiterService(
            StringRedisTemplate redisTemplate,
            @Value("${app.rate-limit.requests-per-window:10}") int limit,
            @Value("${app.rate-limit.window-seconds:60}") long windowSeconds) {
        this.redisTemplate = redisTemplate;
        this.limit = limit;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    /**
     * @throws RateLimitExceededException if the client has exceeded the allowed
     *         request count for the current window.
     */
    public void checkLimit(String clientKey) {
        long windowStart = Instant.now().getEpochSecond() / window.getSeconds();
        String redisKey = "ratelimit:" + clientKey + ":" + windowStart;

        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count != null && count == 1L) {
            // First request in this window — set the key to expire so it's cleaned up automatically.
            redisTemplate.expire(redisKey, window);
        }

        if (count != null && count > limit) {
            long retryAfter = window.getSeconds() - (Instant.now().getEpochSecond() % window.getSeconds());
            throw new RateLimitExceededException(
                    "Rate limit exceeded: max " + limit + " requests per " + window.getSeconds() + "s",
                    retryAfter);
        }
    }
}
