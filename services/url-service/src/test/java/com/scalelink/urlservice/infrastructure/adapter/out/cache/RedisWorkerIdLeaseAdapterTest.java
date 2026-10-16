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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisWorkerIdLeaseAdapterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisWorkerIdLeaseAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RedisWorkerIdLeaseAdapter(redisTemplate);
    }

    @Test
    @DisplayName("acquireWorkerId should lease the first available slot when setIfAbsent succeeds")
    void shouldAcquireFirstAvailableWorkerId() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("snowflake:worker:0"), anyString(), any(Duration.class)))
                .thenReturn(true);

        long workerId = adapter.acquireWorkerId();

        assertThat(workerId).isEqualTo(0L);
    }

    @Test
    @DisplayName("acquireWorkerId should fallback to hostname hashing if Redis throws connection error")
    void shouldFallbackWhenRedisUnavailable() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("Connection refused"));

        long workerId = adapter.acquireWorkerId();

        assertThat(workerId).isBetween(0L, 1023L);
    }

    @Test
    @DisplayName("renewLease should refresh TTL for given worker ID")
    void shouldRenewLease() {
        adapter.renewLease(5L);

        verify(redisTemplate).expire("snowflake:worker:5", Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("releaseLease should delete Redis key upon shutdown")
    void shouldReleaseLease() {
        adapter.releaseLease(5L);

        verify(redisTemplate).delete("snowflake:worker:5");
    }
}
