package com.scalelink.urlservice.infrastructure.adapter.out.cache;

import com.scalelink.urlservice.application.port.out.WorkerIdLeasePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Duration;

/**
 * Dynamic Redis-assisted Worker ID Coordinator for Snowflake Generator.
 * Discovers and leases an unused worker ID (0-1023) upon container boot with a 30s TTL.
 * Falls back to hostname hashing if Redis is temporarily unreachable.
 */
@Slf4j
@Component
public class RedisWorkerIdLeaseAdapter implements WorkerIdLeasePort {

    private static final String WORKER_LEASE_KEY_PREFIX = "snowflake:worker:";
    private static final int MAX_WORKERS = 1024;
    private static final Duration LEASE_DURATION = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;

    public RedisWorkerIdLeaseAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public long acquireWorkerId() {
        String instanceId = resolveInstanceIdentifier();

        try {
            for (long id = 0; id < MAX_WORKERS; id++) {
                String key = WORKER_LEASE_KEY_PREFIX + id;
                Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, instanceId, LEASE_DURATION);
                if (Boolean.TRUE.equals(acquired)) {
                    log.info("Successfully acquired Snowflake worker lease ID: {} for instance: {}", id, instanceId);
                    return id;
                }
            }
        } catch (Exception e) {
            log.warn("Redis lease acquisition error. Falling back to hostname hashing: {}", e.getMessage());
        }

        // Fallback: Deterministic Hostname Hash modulo 1024
        long fallbackId = Math.abs(instanceId.hashCode()) % MAX_WORKERS;
        log.warn("Using fallback Snowflake worker ID: {}", fallbackId);
        return fallbackId;
    }

    @Override
    public void renewLease(long workerId) {
        try {
            String key = WORKER_LEASE_KEY_PREFIX + workerId;
            redisTemplate.expire(key, LEASE_DURATION);
        } catch (Exception e) {
            log.warn("Failed to renew worker lease {}: {}", workerId, e.getMessage());
        }
    }

    @Override
    public void releaseLease(long workerId) {
        try {
            redisTemplate.delete(WORKER_LEASE_KEY_PREFIX + workerId);
            log.info("Released Snowflake worker lease ID: {}", workerId);
        } catch (Exception e) {
            log.warn("Failed to release worker lease {}: {}", workerId, e.getMessage());
        }
    }

    private String resolveInstanceIdentifier() {
        try {
            return InetAddress.getLocalHost().getHostName() + ":" + System.currentTimeMillis();
        } catch (Exception e) {
            return "instance-" + System.currentTimeMillis();
        }
    }
}
