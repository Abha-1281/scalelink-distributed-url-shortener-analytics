package com.scalelink.urlservice.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataUrlRepository extends JpaRepository<UrlJpaEntity, Long> {

    Optional<UrlJpaEntity> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    @Query("SELECT u.shortCode FROM UrlJpaEntity u")
    List<String> findAllShortCodes();
}
