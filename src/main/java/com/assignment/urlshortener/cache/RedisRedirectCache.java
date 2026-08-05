package com.assignment.urlshortener.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "app.cache.enabled", havingValue = "true")
public class RedisRedirectCache implements RedirectCache {

    private static final Logger log = LoggerFactory.getLogger(RedisRedirectCache.class);
    private static final String KEY_PREFIX = "redirect:";

    private final StringRedisTemplate redisTemplate;
    private final Duration defaultTtl;

    public RedisRedirectCache(StringRedisTemplate redisTemplate,
                               @Value("${app.cache.redirect-ttl}") Duration defaultTtl) {
        this.redisTemplate = redisTemplate;
        this.defaultTtl = defaultTtl;
    }

    @Override
    public Optional<String> getOriginalUrl(String shortCode) {
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(key(shortCode)));
        } catch (DataAccessException ex) {
            log.warn("Redis read failed for short code {}, falling back to database", shortCode, ex);
            return Optional.empty();
        }
    }

    @Override
    public void put(String shortCode, String originalUrl, Instant expiresAt) {
        Duration ttl = resolveTtl(expiresAt);
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key(shortCode), originalUrl, ttl);
        } catch (DataAccessException ex) {
            log.warn("Redis write failed for short code {}", shortCode, ex);
        }
    }

    @Override
    public void evict(String shortCode) {
        try {
            redisTemplate.delete(key(shortCode));
        } catch (DataAccessException ex) {
            log.warn("Redis evict failed for short code {}", shortCode, ex);
        }
    }

    private Duration resolveTtl(Instant expiresAt) {
        if (expiresAt == null) {
            return defaultTtl;
        }
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        return remaining.compareTo(defaultTtl) < 0 ? remaining : defaultTtl;
    }

    private String key(String shortCode) {
        return KEY_PREFIX + shortCode;
    }
}
