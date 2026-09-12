package com.shortlink.service;

import com.shortlink.exception.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RateLimiterServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // limit = 3 requests per 60s window, for a fast, readable test
        rateLimiterService = new RateLimiterService(redisTemplate, 3, 60);
    }

    @Test
    void allowsRequestsUnderTheLimit() {
        when(valueOperations.increment(anyString())).thenReturn(1L, 2L, 3L);

        rateLimiterService.checkLimit("client-a");
        rateLimiterService.checkLimit("client-a");
        rateLimiterService.checkLimit("client-a");
        // no exception thrown => success
    }

    @Test
    void setsExpiryOnlyOnFirstRequestInWindow() {
        when(valueOperations.increment(anyString())).thenReturn(1L);

        rateLimiterService.checkLimit("client-b");

        verify(redisTemplate, times(1)).expire(anyString(), any());
    }

    @Test
    void doesNotResetExpiryOnSubsequentRequests() {
        when(valueOperations.increment(anyString())).thenReturn(2L);

        rateLimiterService.checkLimit("client-c");

        verify(redisTemplate, never()).expire(anyString(), any());
    }

    @Test
    void throwsWhenLimitExceeded() {
        when(valueOperations.increment(anyString())).thenReturn(4L);

        assertThrows(RateLimitExceededException.class,
                () -> rateLimiterService.checkLimit("client-d"));
    }

    @Test
    void tracksDifferentClientsIndependently() {
        // Each client's key is distinct, so Redis (and this mock) would track separate counters.
        // Verifying the key passed to increment() contains the client identifier confirms isolation.
        when(valueOperations.increment(anyString())).thenReturn(1L);

        rateLimiterService.checkLimit("client-e");
        rateLimiterService.checkLimit("client-f");

        verify(valueOperations).increment(contains("client-e"));
        verify(valueOperations).increment(contains("client-f"));
    }
}
