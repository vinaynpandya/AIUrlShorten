package com.assignment.urlshortener.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "app.cache.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpRedirectCache implements RedirectCache {

    @Override
    public Optional<String> getOriginalUrl(String shortCode) {
        return Optional.empty();
    }

    @Override
    public void put(String shortCode, String originalUrl, Instant expiresAt) {
        // caching disabled
    }

    @Override
    public void evict(String shortCode) {
        // caching disabled
    }
}
