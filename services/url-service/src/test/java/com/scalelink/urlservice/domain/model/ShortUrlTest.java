package com.scalelink.urlservice.domain.model;

import com.scalelink.urlservice.domain.exception.InvalidUrlException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShortUrlTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://google.com",
            "http://example.com/test?param=1&q=search#section",
            "https://sub.domain.co.uk/path/to/resource",
            "http://localhost:8080/api/v1/urls",
            "https://192.168.1.1:9000/metrics"
    })
    @DisplayName("Should accept valid HTTP and HTTPS URLs")
    void shouldAcceptValidUrls(String validUrl) {
        ShortUrl shortUrl = new ShortUrl(
                1L,
                "validCode",
                validUrl,
                Instant.now(),
                null,
                false
        );

        assertThat(shortUrl.getLongUrl()).isEqualTo(validUrl);
        assertThat(shortUrl.getShortCode()).isEqualTo("validCode");
        assertThat(shortUrl.isExpired()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "ftp://files.example.com",
            "javascript:alert(1)",
            "data:text/html,<h1>Hello</h1>",
            "not-a-valid-url",
            "http://",
            "https://"
    })
    @DisplayName("Should reject invalid or unsafe URL formats")
    void shouldRejectInvalidUrls(String invalidUrl) {
        assertThatThrownBy(() -> new ShortUrl(
                1L,
                "code1",
                invalidUrl,
                Instant.now(),
                null,
                false
        )).isInstanceOf(InvalidUrlException.class);
    }

    @Test
    @DisplayName("Should correctly evaluate isExpired when expiresAt is in past vs future")
    void shouldEvaluateExpiration() {
        Instant past = Instant.now().minus(10, ChronoUnit.MINUTES);
        Instant future = Instant.now().plus(10, ChronoUnit.MINUTES);

        ShortUrl expired = new ShortUrl(1L, "exp1", "https://example.com", Instant.now(), past, false);
        ShortUrl active = new ShortUrl(2L, "act1", "https://example.com", Instant.now(), future, false);
        ShortUrl immortal = new ShortUrl(3L, "imm1", "https://example.com", Instant.now(), null, false);

        assertThat(expired.isExpired()).isTrue();
        assertThat(active.isExpired()).isFalse();
        assertThat(immortal.isExpired()).isFalse();
    }

    @Test
    @DisplayName("Should implement equality and hashCode based on shortCode")
    void shouldVerifyEquality() {
        ShortUrl url1 = new ShortUrl(1L, "sameCode", "https://example.com", Instant.now(), null, false);
        ShortUrl url2 = new ShortUrl(2L, "sameCode", "https://other.com", Instant.now(), null, true);
        ShortUrl url3 = new ShortUrl(3L, "differentCode", "https://example.com", Instant.now(), null, false);

        assertThat(url1).isEqualTo(url2);
        assertThat(url1.hashCode()).isEqualTo(url2.hashCode());
        assertThat(url1).isNotEqualTo(url3);
    }
}
