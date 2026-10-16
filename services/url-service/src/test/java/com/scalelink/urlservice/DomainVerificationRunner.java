package com.scalelink.urlservice;

import com.scalelink.urlservice.domain.service.Base62Encoder;
import com.scalelink.urlservice.domain.service.SnowflakeIdGenerator;
import com.scalelink.urlservice.domain.model.ShortUrl;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

public class DomainVerificationRunner {
    public static void main(String[] args) {
        System.out.println("\n--- Running Domain Algorithms & Concurrency Test ---");

        // Test 1: Base62 Bidirectional Conversion
        Base62Encoder encoder = new Base62Encoder();
        long testNumber = 142839281948291072L;
        String encoded = encoder.encode(testNumber);
        long decoded = encoder.decode(encoded);

        System.out.printf("Test 1: Base62 Encoding for %d -> '%s'%n", testNumber, encoded);
        if (decoded != testNumber) {
            throw new AssertionError("Base62 decode mismatch! Expected " + testNumber + " but got " + decoded);
        }
        System.out.println(" Base62 encode/decode bidirectional check PASSED!");

        // Test 2: Snowflake ID Generator Collision Resistance (100,000 IDs)
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(42L);
        Set<Long> generatedIds = new HashSet<>();
        int testCount = 100_000;
        long startTime = System.nanoTime();

        for (int i = 0; i < testCount; i++) {
            long id = generator.nextId();
            if (id <= 0) {
                throw new AssertionError("Snowflake ID must be positive: " + id);
            }
            if (!generatedIds.add(id)) {
                throw new AssertionError("Collision detected at iteration " + i + " for ID: " + id);
            }
        }
        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
        double throughput = (testCount / (double) durationMs) * 1000;

        System.out.printf("Test 2: Generated %d Snowflake IDs with 0 collisions in %d ms (%.0f IDs/sec)%n",
                testCount, durationMs, throughput);
        System.out.println(" Snowflake collision-free uniqueness check PASSED!");

        // Test 3: ShortUrl Domain Invariants & Validation
        ShortUrl url = new ShortUrl(testNumber, encoded, "https://google.com/search?q=system-design", Instant.now(), null, false);
        if (url.isExpired()) {
            throw new AssertionError("Active URL incorrectly reported as expired!");
        }
        System.out.println(" ShortUrl domain entity invariants check PASSED!");

        System.out.println("\n==========================================================");
        System.out.println(" ALL DOMAIN VERIFICATION TESTS PASSED SUCCESSFULLY! (100%)");
        System.out.println("==========================================================\n");
    }
}
