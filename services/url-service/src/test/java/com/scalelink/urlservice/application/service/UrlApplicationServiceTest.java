package com.scalelink.urlservice.application.service;

import com.scalelink.urlservice.application.port.in.CreateUrlUseCase.CreateUrlCommand;
import com.scalelink.urlservice.application.port.in.RedirectUrlUseCase.ResolveUrlQuery;
import com.scalelink.urlservice.application.port.out.ClickEventPublisherPort;
import com.scalelink.urlservice.application.port.out.UrlCachePort;
import com.scalelink.urlservice.application.port.out.UrlRepositoryPort;
import com.scalelink.urlservice.domain.exception.AliasAlreadyExistsException;
import com.scalelink.urlservice.domain.exception.RateLimitExceededException;
import com.scalelink.urlservice.domain.exception.UrlExpiredException;
import com.scalelink.urlservice.domain.exception.UrlNotFoundException;
import com.scalelink.urlservice.domain.model.ClickEvent;
import com.scalelink.urlservice.domain.model.ShortUrl;
import com.scalelink.urlservice.domain.service.Base62Encoder;
import com.scalelink.urlservice.domain.service.SnowflakeIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlApplicationServiceTest {

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private Base62Encoder base62Encoder;

    @Mock
    private UrlRepositoryPort urlRepositoryPort;

    @Mock
    private UrlCachePort urlCachePort;

    @Mock
    private ClickEventPublisherPort clickEventPublisherPort;

    private UrlApplicationService service;

    @BeforeEach
    void setUp() {
        service = new UrlApplicationService(
                snowflakeIdGenerator,
                base62Encoder,
                urlRepositoryPort,
                urlCachePort,
                clickEventPublisherPort
        );
    }

    @Test
    @DisplayName("createUrl with generated alias should generate ID, save to DB, add to bloom filter and cache")
    void shouldCreateUrlWithGeneratedAlias() {
        CreateUrlCommand command = new CreateUrlCommand("https://example.com/test", null, null, "127.0.0.1");

        when(urlCachePort.isRateLimited(anyString(), anyInt(), any(Duration.class))).thenReturn(false);
        when(snowflakeIdGenerator.nextId()).thenReturn(1001L);
        when(base62Encoder.encode(1001L)).thenReturn("g7");
        when(urlRepositoryPort.save(any(ShortUrl.class))).thenAnswer(inv -> inv.getArgument(0));

        ShortUrl result = service.createUrl(command);

        assertThat(result.getShortCode()).isEqualTo("g7");
        assertThat(result.getLongUrl()).isEqualTo("https://example.com/test");
        assertThat(result.isCustomAlias()).isFalse();

        verify(urlCachePort).addToBloomFilter("g7");
        verify(urlCachePort).put(eq("g7"), eq("https://example.com/test"), any(Duration.class));
        verify(urlRepositoryPort).save(any(ShortUrl.class));
    }

    @Test
    @DisplayName("createUrl with custom alias should verify alias uniqueness and save")
    void shouldCreateUrlWithCustomAlias() {
        CreateUrlCommand command = new CreateUrlCommand("https://example.com/promo", "summer24", null, "127.0.0.1");

        when(urlCachePort.isRateLimited(anyString(), anyInt(), any(Duration.class))).thenReturn(false);
        when(snowflakeIdGenerator.nextId()).thenReturn(1002L);
        when(urlRepositoryPort.existsByShortCode("summer24")).thenReturn(false);
        when(urlRepositoryPort.save(any(ShortUrl.class))).thenAnswer(inv -> inv.getArgument(0));

        ShortUrl result = service.createUrl(command);

        assertThat(result.getShortCode()).isEqualTo("summer24");
        assertThat(result.isCustomAlias()).isTrue();
        verify(urlCachePort).addToBloomFilter("summer24");
        verify(urlCachePort).put(eq("summer24"), eq("https://example.com/promo"), any(Duration.class));
    }

    @Test
    @DisplayName("createUrl should throw AliasAlreadyExistsException if custom alias is taken")
    void shouldThrowWhenCustomAliasAlreadyExists() {
        CreateUrlCommand command = new CreateUrlCommand("https://example.com/promo", "takenAlias", null, "127.0.0.1");

        when(urlCachePort.isRateLimited(anyString(), anyInt(), any(Duration.class))).thenReturn(false);
        when(snowflakeIdGenerator.nextId()).thenReturn(1003L);
        when(urlRepositoryPort.existsByShortCode("takenAlias")).thenReturn(true);

        assertThatThrownBy(() -> service.createUrl(command))
                .isInstanceOf(AliasAlreadyExistsException.class);

        verify(urlRepositoryPort, never()).save(any());
        verify(urlCachePort, never()).addToBloomFilter(anyString());
    }

    @Test
    @DisplayName("createUrl should throw RateLimitExceededException when rate limit threshold exceeded")
    void shouldThrowWhenRateLimitExceeded() {
        CreateUrlCommand command = new CreateUrlCommand("https://example.com/spam", null, null, "192.168.1.50");

        when(urlCachePort.isRateLimited(eq("ratelimit:192.168.1.50"), eq(60), any(Duration.class))).thenReturn(true);

        assertThatThrownBy(() -> service.createUrl(command))
                .isInstanceOf(RateLimitExceededException.class);

        verifyNoInteractions(snowflakeIdGenerator, urlRepositoryPort);
    }

    @Test
    @DisplayName("resolveUrl should reject in 0.2ms via Bloom Filter without touching Redis cache or DB")
    void shouldRejectImmediatelyWhenBloomFilterNegative() {
        ResolveUrlQuery query = new ResolveUrlQuery("nonExistent", "10.0.0.1", "curl/8.0", null);

        when(urlCachePort.mightContain("nonExistent")).thenReturn(false);

        assertThatThrownBy(() -> service.resolveUrl(query))
                .isInstanceOf(UrlNotFoundException.class);

        verify(urlCachePort, never()).get(anyString());
        verifyNoInteractions(urlRepositoryPort, clickEventPublisherPort);
    }

    @Test
    @DisplayName("resolveUrl should return cached target on cache hit and publish async click event")
    void shouldReturnCachedTargetOnCacheHit() {
        ResolveUrlQuery query = new ResolveUrlQuery("hotLink", "10.0.0.1", "Mozilla/5.0", "https://news.ycombinator.com");

        when(urlCachePort.mightContain("hotLink")).thenReturn(true);
        when(urlCachePort.get("hotLink")).thenReturn(Optional.of("https://example.com/trending"));

        String resolved = service.resolveUrl(query);

        assertThat(resolved).isEqualTo("https://example.com/trending");
        verifyNoInteractions(urlRepositoryPort); // Zero DB lookups

        ArgumentCaptor<ClickEvent> eventCaptor = ArgumentCaptor.forClass(ClickEvent.class);
        verify(clickEventPublisherPort).publish(eventCaptor.capture());
        ClickEvent captured = eventCaptor.getValue();
        assertThat(captured.shortCode()).isEqualTo("hotLink");
        assertThat(captured.originalUrl()).isEqualTo("https://example.com/trending");
        assertThat(captured.referer()).isEqualTo("https://news.ycombinator.com");
    }

    @Test
    @DisplayName("resolveUrl on cache miss should query DB, re-populate cache, and dispatch event")
    void shouldFallbackToDatabaseOnCacheMiss() {
        ResolveUrlQuery query = new ResolveUrlQuery("missLink", "10.0.0.1", "curl/8.0", null);
        ShortUrl dbRecord = new ShortUrl(200L, "missLink", "https://example.com/cold", Instant.now(), null, false);

        when(urlCachePort.mightContain("missLink")).thenReturn(true);
        when(urlCachePort.get("missLink")).thenReturn(Optional.empty());
        when(urlRepositoryPort.findByShortCode("missLink")).thenReturn(Optional.of(dbRecord));

        String resolved = service.resolveUrl(query);

        assertThat(resolved).isEqualTo("https://example.com/cold");
        verify(urlCachePort).put(eq("missLink"), eq("https://example.com/cold"), any(Duration.class));
        verify(clickEventPublisherPort).publish(any(ClickEvent.class));
    }

    @Test
    @DisplayName("resolveUrl should throw UrlExpiredException if link is expired")
    void shouldThrowWhenUrlIsExpired() {
        ResolveUrlQuery query = new ResolveUrlQuery("expiredLink", "10.0.0.1", "curl/8.0", null);
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        ShortUrl expiredRecord = new ShortUrl(300L, "expiredLink", "https://example.com/old", Instant.now().minus(2, ChronoUnit.HOURS), past, false);

        when(urlCachePort.mightContain("expiredLink")).thenReturn(true);
        when(urlCachePort.get("expiredLink")).thenReturn(Optional.empty());
        when(urlRepositoryPort.findByShortCode("expiredLink")).thenReturn(Optional.of(expiredRecord));

        assertThatThrownBy(() -> service.resolveUrl(query))
                .isInstanceOf(UrlExpiredException.class);

        verify(clickEventPublisherPort, never()).publish(any());
    }
}
