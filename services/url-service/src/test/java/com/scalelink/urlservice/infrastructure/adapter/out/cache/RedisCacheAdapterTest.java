package com.scalelink.urlservice.infrastructure.adapter.out.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisCacheAdapterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisCacheAdapter adapter;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        adapter = new RedisCacheAdapter(redisTemplate);
    }

    @Test
    @DisplayName("get should return cached URL when present")
    void shouldReturnCachedUrl() {
        when(valueOperations.get("url:testCode")).thenReturn("https://example.com/target");

        Optional<String> result = adapter.get("testCode");

        assertThat(result).contains("https://example.com/target");
    }

    @Test
    @DisplayName("get should gracefully return empty Optional on Redis connection failure")
    void shouldGracefullyFallbackOnGetError() {
        when(valueOperations.get(anyString())).thenThrow(new RedisConnectionFailureException("Connection refused"));

        Optional<String> result = adapter.get("testCode");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("put should store URL with configured TTL")
    void shouldPutUrlWithTtl() {
        Duration ttl = Duration.ofHours(24);
        adapter.put("code1", "https://example.com", ttl);

        verify(valueOperations).set("url:code1", "https://example.com", ttl);
    }

    @Test
    @DisplayName("mightContain should return false when any bloom bit is unset")
    void shouldReturnFalseWhenBitUnset() {
        when(valueOperations.getBit(eq("bloom:urls"), anyLong())).thenReturn(false);

        boolean result = adapter.mightContain("nonExistent");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("mightContain should return true when all bloom bits are set")
    void shouldReturnTrueWhenAllBitsSet() {
        when(valueOperations.getBit(eq("bloom:urls"), anyLong())).thenReturn(true);

        boolean result = adapter.mightContain("existingCode");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("mightContain should fail-open (return true) on Redis error to prevent false rejections")
    void shouldFailOpenOnBloomError() {
        when(valueOperations.getBit(anyString(), anyLong()))
                .thenThrow(new RedisConnectionFailureException("Timeout"));

        boolean result = adapter.mightContain("anyCode");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isRateLimited should return true when count exceeds maximum allowed")
    void shouldDetectRateLimitExceeded() {
        when(valueOperations.increment("ratelimit:192.168.1.1")).thenReturn(61L);

        boolean limited = adapter.isRateLimited("ratelimit:192.168.1.1", 60, Duration.ofMinutes(1));

        assertThat(limited).isTrue();
    }

    @Test
    @DisplayName("isRateLimited should return false when count is within quota")
    void shouldAllowWhenWithinLimit() {
        when(valueOperations.increment("ratelimit:192.168.1.1")).thenReturn(15L);

        boolean limited = adapter.isRateLimited("ratelimit:192.168.1.1", 60, Duration.ofMinutes(1));

        assertThat(limited).isFalse();
    }

    @Test
    @DisplayName("isRateLimited should set expiration window on first increment")
    void shouldSetExpirationOnFirstIncrement() {
        when(valueOperations.increment("ratelimit:new_user")).thenReturn(1L);

        adapter.isRateLimited("ratelimit:new_user", 60, Duration.ofMinutes(1));

        verify(redisTemplate).expire("ratelimit:new_user", Duration.ofMinutes(1));
    }
}
