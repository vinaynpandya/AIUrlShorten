package com.assignment.urlshortener.dto;

import java.time.Instant;

public record UrlAnalyticsResponse(
        String shortCode,
        String originalUrl,
        long clickCount,
        Instant createdAt,
        Instant lastAccessedAt,
        boolean active,
        Instant expiresAt
) {
}
