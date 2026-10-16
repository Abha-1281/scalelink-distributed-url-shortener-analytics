package com.scalelink.urlservice.infrastructure.config;

import com.scalelink.urlservice.application.port.out.UrlCachePort;
import com.scalelink.urlservice.application.port.out.UrlRepositoryPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Pre-warms the Redis Bloom Filter with existing short codes on boot.
 * Protects against false rejections if Redis was restarted or cold.
 */
@Slf4j
@Component
public class BloomFilterWarmupRunner implements ApplicationRunner {

    private final UrlRepositoryPort urlRepositoryPort;
    private final UrlCachePort urlCachePort;

    public BloomFilterWarmupRunner(UrlRepositoryPort urlRepositoryPort, UrlCachePort urlCachePort) {
        this.urlRepositoryPort = urlRepositoryPort;
        this.urlCachePort = urlCachePort;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("Starting Bloom filter cache pre-warming from database...");
            List<String> shortCodes = urlRepositoryPort.findAllShortCodes();
            for (String code : shortCodes) {
                urlCachePort.addToBloomFilter(code);
            }
            log.info("Bloom filter pre-warming completed. Indexed {} existing short codes.", shortCodes.size());
        } catch (Exception e) {
            log.warn("Could not pre-warm Bloom filter on startup: {}", e.getMessage());
        }
    }
}
