package com.scalelink.urlservice.domain.model;

import com.scalelink.urlservice.domain.exception.InvalidUrlException;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Objects;

/**
 * Pure Domain Entity representing a Shortened URL.
 * Contains core domain invariants and business rules with ZERO framework dependencies.
 */
public class ShortUrl {

    private final Long id;
    private final String shortCode;
    private final String longUrl;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final boolean customAlias;

    public ShortUrl(Long id, String shortCode, String longUrl, Instant createdAt, Instant expiresAt, boolean customAlias) {
        validateLongUrl(longUrl);
        this.id = Objects.requireNonNull(id, "ID cannot be null");
        this.shortCode = Objects.requireNonNull(shortCode, "Short code cannot be null");
        this.longUrl = longUrl;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.expiresAt = expiresAt;
        this.customAlias = customAlias;
    }

    private void validateLongUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new InvalidUrlException("Invalid URL: URL cannot be null or blank");
        }
        if (url.length() > 2048) {
            throw new InvalidUrlException("Invalid URL: Exceeds maximum length of 2048 characters");
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new InvalidUrlException("Invalid URL protocol: Must begin with http:// or https://");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new InvalidUrlException("Invalid URL: Host cannot be empty");
            }
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("Invalid URL format: " + e.getMessage());
        }
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public Long getId() {
        return id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getLongUrl() {
        return longUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isCustomAlias() {
        return customAlias;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShortUrl shortUrl)) return false;
        return Objects.equals(shortCode, shortUrl.shortCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shortCode);
    }
}
