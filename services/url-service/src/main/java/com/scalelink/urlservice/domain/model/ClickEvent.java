package com.scalelink.urlservice.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable Domain Record representing a Click Event captured during redirection.
 * Dispatched asynchronously to Apache Kafka.
 */
public record ClickEvent(
        String eventId,
        String shortCode,
        String originalUrl,
        Instant timestamp,
        String ipAddress,
        String userAgent,
        String referer
) {
    public static ClickEvent create(String shortCode, String originalUrl, String ipAddress, String userAgent, String referer) {
        return new ClickEvent(
                UUID.randomUUID().toString(),
                shortCode,
                originalUrl,
                Instant.now(),
                ipAddress != null ? ipAddress : "UNKNOWN",
                userAgent != null ? userAgent : "UNKNOWN",
                referer != null ? referer : "DIRECT"
        );
    }
}
