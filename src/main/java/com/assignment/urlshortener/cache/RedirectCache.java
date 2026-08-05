package com.assignment.urlshortener.cache;

import java.time.Instant;
import java.util.Optional;

public interface RedirectCache {

    Optional<String> getOriginalUrl(String shortCode);

    void put(String shortCode, String originalUrl, Instant expiresAt);

    void evict(String shortCode);
}
