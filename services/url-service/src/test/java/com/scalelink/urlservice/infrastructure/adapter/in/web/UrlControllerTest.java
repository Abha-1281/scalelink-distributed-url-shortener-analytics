package com.scalelink.urlservice.infrastructure.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scalelink.urlservice.application.port.in.CreateUrlUseCase;
import com.scalelink.urlservice.application.port.in.RedirectUrlUseCase;
import com.scalelink.urlservice.domain.exception.AliasAlreadyExistsException;
import com.scalelink.urlservice.domain.exception.RateLimitExceededException;
import com.scalelink.urlservice.domain.exception.UrlExpiredException;
import com.scalelink.urlservice.domain.exception.UrlNotFoundException;
import com.scalelink.urlservice.domain.model.ShortUrl;
import com.scalelink.urlservice.infrastructure.adapter.in.web.dto.CreateUrlRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {UrlController.class, GlobalExceptionHandler.class})
class UrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CreateUrlUseCase createUrlUseCase;

    @MockBean
    private RedirectUrlUseCase redirectUrlUseCase;

    @Test
    @DisplayName("POST /api/v1/urls should return 201 Created with full payload")
    void shouldCreateUrlSuccessfully() throws Exception {
        CreateUrlRequest request = new CreateUrlRequest("https://example.com/long-page", "myLink", null);
        ShortUrl created = new ShortUrl(101L, "myLink", "https://example.com/long-page", Instant.now(), null, true);

        when(createUrlUseCase.createUrl(any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.shortCode").value("myLink"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost/myLink"))
                .andExpect(jsonPath("$.longUrl").value("https://example.com/long-page"))
                .andExpect(jsonPath("$.customAlias").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/urls should return 400 Bad Request when longUrl is blank")
    void shouldReturn400WhenUrlIsBlank() throws Exception {
        CreateUrlRequest request = new CreateUrlRequest("", null, null);

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Error"));
    }

    @Test
    @DisplayName("POST /api/v1/urls should return 409 Conflict when alias is already taken")
    void shouldReturn409WhenAliasExists() throws Exception {
        CreateUrlRequest request = new CreateUrlRequest("https://example.com", "duplicate", null);

        when(createUrlUseCase.createUrl(any())).thenThrow(new AliasAlreadyExistsException("duplicate"));

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Custom alias already in use: duplicate"));
    }

    @Test
    @DisplayName("POST /api/v1/urls should return 429 Too Many Requests when rate limited")
    void shouldReturn429WhenRateLimited() throws Exception {
        CreateUrlRequest request = new CreateUrlRequest("https://example.com", null, null);

        when(createUrlUseCase.createUrl(any())).thenThrow(new RateLimitExceededException("Rate limit exceeded"));

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    @DisplayName("GET /{shortCode} should return 302 Found with Location header and no-cache directives")
    void shouldRedirectWith302AndNoCacheHeaders() throws Exception {
        when(redirectUrlUseCase.resolveUrl(any())).thenReturn("https://example.com/destination");

        mockMvc.perform(get("/test1234")
                        .header(HttpHeaders.USER_AGENT, "Mozilla/5.0")
                        .header("X-Forwarded-For", "203.0.113.195"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "https://example.com/destination"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, max-age=0, must-revalidate"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"));
    }

    @Test
    @DisplayName("GET /{shortCode} should return 404 Not Found when code does not exist")
    void shouldReturn404WhenNotFound() throws Exception {
        when(redirectUrlUseCase.resolveUrl(any())).thenThrow(new UrlNotFoundException("unknownCode"));

        mockMvc.perform(get("/unknownCode"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Short URL not found: unknownCode"));
    }

    @Test
    @DisplayName("GET /{shortCode} should return 410 Gone when link is expired")
    void shouldReturn410WhenExpired() throws Exception {
        when(redirectUrlUseCase.resolveUrl(any())).thenThrow(new UrlExpiredException("expiredCode"));

        mockMvc.perform(get("/expiredCode"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.message").value("Short URL has expired: expiredCode"));
    }
}
