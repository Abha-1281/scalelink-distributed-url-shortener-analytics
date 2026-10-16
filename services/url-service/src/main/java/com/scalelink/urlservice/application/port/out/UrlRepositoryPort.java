package com.scalelink.urlservice.application.port.out;

import com.scalelink.urlservice.domain.model.ShortUrl;

import java.util.List;
import java.util.Optional;

public interface UrlRepositoryPort {

    ShortUrl save(ShortUrl shortUrl);

    Optional<ShortUrl> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    List<String> findAllShortCodes();
}
