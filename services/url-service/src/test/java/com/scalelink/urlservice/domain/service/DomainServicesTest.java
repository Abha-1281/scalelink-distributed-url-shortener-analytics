package com.scalelink.urlservice.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainServicesTest {

    @Test
    @DisplayName("Base62Encoder should accurately encode and decode positive long values")
    void shouldEncodeAndDecodeBase62() {
        Base62Encoder encoder = new Base62Encoder();

        long original = 123456789012345L;
        String encoded = encoder.encode(original);

        assertThat(encoded).isNotBlank();
        assertThat(encoder.decode(encoded)).isEqualTo(original);
    }

    @Test
    @DisplayName("Base62Encoder should encode zero correctly")
    void shouldEncodeZero() {
        Base62Encoder encoder = new Base62Encoder();
        assertThat(encoder.encode(0)).isEqualTo("0");
        assertThat(encoder.decode("0")).isEqualTo(0L);
    }

    @Test
    @DisplayName("SnowflakeIdGenerator should produce 10,000 unique IDs without collision")
    void shouldGenerateUniqueSnowflakeIds() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L);
        Set<Long> ids = new HashSet<>();

        int count = 10_000;
        for (int i = 0; i < count; i++) {
            long id = generator.nextId();
            assertThat(id).isPositive();
            ids.add(id);
        }

        assertThat(ids).hasSize(count);
    }

    @Test
    @DisplayName("SnowflakeIdGenerator should reject invalid worker IDs outside [0, 1023]")
    void shouldRejectInvalidWorkerId() {
        assertThatThrownBy(() -> new SnowflakeIdGenerator(1024L))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new SnowflakeIdGenerator(-1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
