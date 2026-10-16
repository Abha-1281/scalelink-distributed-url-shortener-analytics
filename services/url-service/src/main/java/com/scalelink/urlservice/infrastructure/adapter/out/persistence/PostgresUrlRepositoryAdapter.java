package com.scalelink.urlservice.infrastructure.adapter.out.persistence;

import com.scalelink.urlservice.application.port.out.UrlRepositoryPort;
import com.scalelink.urlservice.domain.model.ShortUrl;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class PostgresUrlRepositoryAdapter implements UrlRepositoryPort {

    private final SpringDataUrlRepository repository;

    public PostgresUrlRepositoryAdapter(SpringDataUrlRepository repository) {
        this.repository = repository;
    }

    @Override
    public ShortUrl save(ShortUrl shortUrl) {
        UrlJpaEntity entity = toEntity(shortUrl);
        UrlJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<ShortUrl> findByShortCode(String shortCode) {
        return repository.findByShortCode(shortCode).map(this::toDomain);
    }

    @Override
    public boolean existsByShortCode(String shortCode) {
        return repository.existsByShortCode(shortCode);
    }

    @Override
    public List<String> findAllShortCodes() {
        return repository.findAllShortCodes();
    }

    private UrlJpaEntity toEntity(ShortUrl domain) {
        return new UrlJpaEntity(
                domain.getId(),
                domain.getShortCode(),
                domain.getLongUrl(),
                domain.getCreatedAt(),
                domain.getExpiresAt(),
                domain.isCustomAlias()
        );
    }

    private ShortUrl toDomain(UrlJpaEntity entity) {
        return new ShortUrl(
                entity.getId(),
                entity.getShortCode(),
                entity.getLongUrl(),
                entity.getCreatedAt(),
                entity.getExpiresAt(),
                entity.isCustomAlias()
        );
    }
}
