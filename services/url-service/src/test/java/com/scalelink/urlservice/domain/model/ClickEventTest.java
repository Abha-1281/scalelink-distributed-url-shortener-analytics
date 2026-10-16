package com.scalelink.urlservice.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClickEventTest {

    @Test
    @DisplayName("Should create ClickEvent with provided parameters and generate UUID eventId")
    void shouldCreateClickEventWithProvidedValues() {
        ClickEvent event = ClickEvent.create(
                "abc1234",
                "https://example.com/target",
                "203.0.113.195",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                "https://google.com"
        );

        assertThat(event.eventId()).isNotBlank();
        assertThat(event.shortCode()).isEqualTo("abc1234");
        assertThat(event.originalUrl()).isEqualTo("https://example.com/target");
        assertThat(event.ipAddress()).isEqualTo("203.0.113.195");
        assertThat(event.userAgent()).contains("Windows");
        assertThat(event.referer()).isEqualTo("https://google.com");
        assertThat(event.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("Should fallback gracefully when optional headers are null")
    void shouldHandleNullOptionalHeaders() {
        ClickEvent event = ClickEvent.create(
                "abc1234",
                "https://example.com/target",
                null,
                null,
                null
        );

        assertThat(event.ipAddress()).isEqualTo("UNKNOWN");
        assertThat(event.userAgent()).isEqualTo("UNKNOWN");
        assertThat(event.referer()).isEqualTo("DIRECT");
    }
}
