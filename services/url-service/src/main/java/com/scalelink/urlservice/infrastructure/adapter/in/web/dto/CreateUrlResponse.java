package com.scalelink.urlservice.infrastructure.adapter.in.web.dto;

import com.scalelink.urlservice.domain.model.ShortUrl;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUrlResponse {
    private Long id;
    private String shortCode;
    private String shortUrl;
    private String longUrl;
    private Instant createdAt;
    private Instant expiresAt;
    private boolean customAlias;

    public static CreateUrlResponse fromDomain(ShortUrl url, String baseUrl) {
        String fullShortUrl = (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + url.getShortCode();
        return CreateUrlResponse.builder()
                .id(url.getId())
                .shortCode(url.getShortCode())
                .shortUrl(fullShortUrl)
                .longUrl(url.getLongUrl())
                .createdAt(url.getCreatedAt())
                .expiresAt(url.getExpiresAt())
                .customAlias(url.isCustomAlias())
                .build();
    }
}
