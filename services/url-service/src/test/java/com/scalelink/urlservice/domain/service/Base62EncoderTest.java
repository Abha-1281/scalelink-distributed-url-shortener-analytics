package com.scalelink.urlservice.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Base62EncoderTest {

    private Base62Encoder encoder;

    @BeforeEach
    void setUp() {
        encoder = new Base62Encoder();
    }

    @Test
    @DisplayName("Should encode zero to '0' and decode back to 0")
    void shouldEncodeAndDecodeZero() {
        assertThat(encoder.encode(0)).isEqualTo("0");
        assertThat(encoder.decode("0")).isEqualTo(0L);
    }

    @Test
    @DisplayName("Should round-trip encode and decode Long.MAX_VALUE")
    void shouldEncodeAndDecodeMaxLong() {
        long original = Long.MAX_VALUE;
        String encoded = encoder.encode(original);

        assertThat(encoded).isNotBlank();
        assertThat(encoder.decode(encoded)).isEqualTo(original);
    }

    @ParameterizedTest
    @ValueSource(longs = {1L, 61L, 62L, 63L, 1000L, 9999999999L, 123456789012345L})
    @DisplayName("Should accurately encode and decode positive long values")
    void shouldEncodeAndDecodePositiveValues(long value) {
        String encoded = encoder.encode(value);
        assertThat(encoded).isNotBlank();
        assertThat(encoder.decode(encoded)).isEqualTo(value);
    }

    @Test
    @DisplayName("Should reject negative values during encoding")
    void shouldRejectNegativeValues() {
        assertThatThrownBy(() -> encoder.encode(-1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Value must be non-negative");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "abc-def", "abc@123", "a b c"})
    @DisplayName("Should reject blank or invalid strings during decoding")
    void shouldRejectInvalidStringsOnDecode(String invalidStr) {
        assertThatThrownBy(() -> encoder.decode(invalidStr))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
