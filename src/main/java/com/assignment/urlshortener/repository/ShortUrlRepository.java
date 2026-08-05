package com.assignment.urlshortener.repository;

import com.assignment.urlshortener.entity.ShortUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    Optional<ShortUrl> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    @Modifying
    @Query("UPDATE ShortUrl s SET s.clickCount = s.clickCount + 1, s.lastAccessedAt = :accessedAt "
            + "WHERE s.shortCode = :shortCode AND s.active = true")
    int incrementClickCount(@Param("shortCode") String shortCode, @Param("accessedAt") Instant accessedAt);
}
