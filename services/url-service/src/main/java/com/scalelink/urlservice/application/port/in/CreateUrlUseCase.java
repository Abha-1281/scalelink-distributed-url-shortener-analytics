package com.scalelink.urlservice.application.port.in;

import com.scalelink.urlservice.domain.model.ShortUrl;

import java.time.Instant;

public interface CreateUrlUseCase {

    record CreateUrlCommand(
            String originalUrl,
            String customAlias,
            Instant expiresAt,
            String clientIp
    ) {}

    ShortUrl createUrl(CreateUrlCommand command);
}
