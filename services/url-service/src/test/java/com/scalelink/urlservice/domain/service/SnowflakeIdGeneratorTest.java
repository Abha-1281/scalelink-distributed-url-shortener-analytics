package com.scalelink.urlservice.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnowflakeIdGeneratorTest {

    @Test
    @DisplayName("Should generate 20,000 unique sequential IDs without collision")
    void shouldGenerateUniqueSnowflakeIds() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(5L);
        Set<Long> ids = new HashSet<>();

        int count = 20_000;
        long previousId = 0L;
        for (int i = 0; i < count; i++) {
            long currentId = generator.nextId();
            assertThat(currentId).isPositive();
            assertThat(currentId).isGreaterThan(previousId);
            ids.add(currentId);
            previousId = currentId;
        }

        assertThat(ids).hasSize(count);
    }

    @Test
    @DisplayName("Should embed worker ID in bit position [12..21]")
    void shouldEmbedWorkerIdCorrectly() {
        long workerId = 42L;
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(workerId);
        long id = generator.nextId();

        // Extract worker ID by shifting right 12 bits and masking 10 bits (0x3FF = 1023)
        long extractedWorkerId = (id >> 12) & 0x3FF;
        assertThat(extractedWorkerId).isEqualTo(workerId);
    }

    @Test
    @DisplayName("Should reject worker ID out of range [0..1023]")
    void shouldRejectInvalidWorkerId() {
        assertThatThrownBy(() -> new SnowflakeIdGenerator(-1L))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new SnowflakeIdGenerator(1024L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
