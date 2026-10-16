package com.scalelink.urlservice.application.port.out;

import java.time.Duration;
import java.util.Optional;

public interface UrlCachePort {

    Optional<String> get(String shortCode);

    void put(String shortCode, String longUrl, Duration ttl);

    boolean mightContain(String shortCode);

    void addToBloomFilter(String shortCode);

    boolean isRateLimited(String key, int maxRequests, Duration window);
}
