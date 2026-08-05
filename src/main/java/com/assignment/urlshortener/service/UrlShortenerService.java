package com.assignment.urlshortener.service;

import com.assignment.urlshortener.dto.CreateShortUrlRequest;
import com.assignment.urlshortener.dto.CreateShortUrlResponse;
import com.assignment.urlshortener.dto.UrlAnalyticsResponse;
import com.assignment.urlshortener.entity.ShortUrl;
import com.assignment.urlshortener.exception.ShortCodeGenerationException;
import com.assignment.urlshortener.exception.ShortUrlExpiredException;
import com.assignment.urlshortener.exception.ShortUrlNotFoundException;
import com.assignment.urlshortener.repository.ShortUrlRepository;
import com.assignment.urlshortener.util.ShortCodeGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class UrlShortenerService {

    private static final Logger log = LoggerFactory.getLogger(UrlShortenerService.class);
    private static final int MAX_GENERATION_ATTEMPTS = 5;

    private final ShortUrlRepository shortUrlRepository;
    private final ShortCodeGenerator shortCodeGenerator;
    private final String baseUrl;

    public UrlShortenerService(ShortUrlRepository shortUrlRepository,
                                ShortCodeGenerator shortCodeGenerator,
                                @Value("${app.base-url}") String baseUrl) {
        this.shortUrlRepository = shortUrlRepository;
        this.shortCodeGenerator = shortCodeGenerator;
        this.baseUrl = baseUrl;
    }

    @Transactional
    public CreateShortUrlResponse createShortUrl(CreateShortUrlRequest request) {
        String shortCode = generateUniqueShortCode();
        Instant createdAt = Instant.now();

        ShortUrl shortUrl = new ShortUrl(request.originalUrl(), shortCode, createdAt, request.expiresAt());
        shortUrlRepository.save(shortUrl);

        log.info("Created short URL with code {}", shortCode);
        return new CreateShortUrlResponse(shortCode, buildShortUrl(shortCode), request.originalUrl(), createdAt,
                request.expiresAt());
    }

    @Transactional
    public String resolveOriginalUrl(String shortCode) {
        ShortUrl shortUrl = shortUrlRepository.findByShortCode(shortCode)
                .filter(ShortUrl::isActive)
                .orElseThrow(() -> {
                    log.warn("Short code not found or inactive: {}", shortCode);
                    return new ShortUrlNotFoundException(shortCode);
                });

        if (shortUrl.isExpired()) {
            log.warn("Short code expired: {}", shortCode);
            throw new ShortUrlExpiredException(shortCode);
        }

        shortUrlRepository.incrementClickCount(shortCode, Instant.now());
        return shortUrl.getOriginalUrl();
    }

    @Transactional(readOnly = true)
    public UrlAnalyticsResponse getAnalytics(String shortCode) {
        ShortUrl shortUrl = shortUrlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> {
                    log.warn("Short code not found: {}", shortCode);
                    return new ShortUrlNotFoundException(shortCode);
                });

        return new UrlAnalyticsResponse(
                shortUrl.getShortCode(),
                shortUrl.getOriginalUrl(),
                shortUrl.getClickCount(),
                shortUrl.getCreatedAt(),
                shortUrl.getLastAccessedAt(),
                shortUrl.isActive(),
                shortUrl.getExpiresAt()
        );
    }

    private String generateUniqueShortCode() {
        for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
            String candidate = shortCodeGenerator.generate();
            if (!shortUrlRepository.existsByShortCode(candidate)) {
                return candidate;
            }
            log.warn("Short code collision on attempt {}: {}", attempt, candidate);
        }
        throw new ShortCodeGenerationException(
                "Failed to generate a unique short code after " + MAX_GENERATION_ATTEMPTS + " attempts");
    }

    private String buildShortUrl(String shortCode) {
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalizedBaseUrl + "/" + shortCode;
    }
}
