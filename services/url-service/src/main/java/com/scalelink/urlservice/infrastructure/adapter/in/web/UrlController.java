package com.scalelink.urlservice.infrastructure.adapter.in.web;

import com.scalelink.urlservice.application.port.in.CreateUrlUseCase;
import com.scalelink.urlservice.application.port.in.RedirectUrlUseCase;
import com.scalelink.urlservice.domain.model.ShortUrl;
import com.scalelink.urlservice.infrastructure.adapter.in.web.dto.CreateUrlRequest;
import com.scalelink.urlservice.infrastructure.adapter.in.web.dto.CreateUrlResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@Slf4j
@RestController
public class UrlController {

    private final CreateUrlUseCase createUrlUseCase;
    private final RedirectUrlUseCase redirectUrlUseCase;
    private final String baseUrl;

    public UrlController(
            CreateUrlUseCase createUrlUseCase,
            RedirectUrlUseCase redirectUrlUseCase,
            @Value("${app.base-url:http://localhost}") String baseUrl
    ) {
        this.createUrlUseCase = createUrlUseCase;
        this.redirectUrlUseCase = redirectUrlUseCase;
        this.baseUrl = baseUrl;
    }

    /**
     * URL Creation API Endpoint
     * POST /api/v1/urls
     */
    @PostMapping("/api/v1/urls")
    public ResponseEntity<CreateUrlResponse> createShortUrl(
            @Valid @RequestBody CreateUrlRequest request,
            HttpServletRequest servletRequest
    ) {
        String clientIp = extractClientIp(servletRequest);

        CreateUrlUseCase.CreateUrlCommand command = new CreateUrlUseCase.CreateUrlCommand(
                request.getLongUrl(),
                request.getCustomAlias(),
                request.getExpiresAt(),
                clientIp
        );

        ShortUrl shortUrl = createUrlUseCase.createUrl(command);
        CreateUrlResponse response = CreateUrlResponse.fromDomain(shortUrl, baseUrl);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Ultra-Low-Latency Redirection Endpoint
     * GET /{shortCode}
     * Returns HTTP 302 Found to prevent aggressive browser caching while preserving telemetry accuracy.
     */
    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(
            @PathVariable String shortCode,
            HttpServletRequest servletRequest
    ) {
        String clientIp = extractClientIp(servletRequest);
        String userAgent = servletRequest.getHeader(HttpHeaders.USER_AGENT);
        String referer = servletRequest.getHeader(HttpHeaders.REFERER);

        RedirectUrlUseCase.ResolveUrlQuery query = new RedirectUrlUseCase.ResolveUrlQuery(
                shortCode,
                clientIp,
                userAgent,
                referer
        );

        String targetUrl = redirectUrlUseCase.resolveUrl(query);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(targetUrl))
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, max-age=0, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .build();
    }

    private String extractClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isBlank()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
