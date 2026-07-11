package com.cutegoals.instancemanagement.service;

import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterServiceTest {

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterService = new RateLimiterService();
    }

    @Test
    void shouldAllowRequestsUnderLimit() {
        // Should not throw
        rateLimiterService.checkRateLimit("test-key", 5, 60_000);
        rateLimiterService.checkRateLimit("test-key", 5, 60_000);
        rateLimiterService.checkRateLimit("test-key", 5, 60_000);
    }

    @Test
    void shouldThrowWhenLimitExceeded() {
        String key = "exceed-key";
        rateLimiterService.checkRateLimit(key, 3, 60_000);
        rateLimiterService.checkRateLimit(key, 3, 60_000);
        rateLimiterService.checkRateLimit(key, 3, 60_000);

        assertThrows(BusinessException.class, () ->
                        rateLimiterService.checkRateLimit(key, 3, 60_000),
                ErrorCode.RATE_LIMITED.getCode());
    }

    @Test
    void shouldResetRateLimit() {
        String key = "reset-key";
        rateLimiterService.checkRateLimit(key, 2, 60_000);
        rateLimiterService.checkRateLimit(key, 2, 60_000);

        assertThrows(BusinessException.class, () ->
                rateLimiterService.checkRateLimit(key, 2, 60_000));

        rateLimiterService.resetRateLimit(key);

        // Should work again after reset
        rateLimiterService.checkRateLimit(key, 2, 60_000);
    }

    @Test
    void differentKeysShouldNotInterfere() {
        rateLimiterService.checkRateLimit("key-a", 1, 60_000);
        assertThrows(BusinessException.class, () ->
                rateLimiterService.checkRateLimit("key-a", 1, 60_000));

        // Different key should still work
        rateLimiterService.checkRateLimit("key-b", 1, 60_000);
    }
}
