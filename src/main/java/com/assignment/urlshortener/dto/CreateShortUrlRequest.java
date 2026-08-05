package com.assignment.urlshortener.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.time.Instant;
import java.util.Locale;

public record CreateShortUrlRequest(
        @NotBlank
        @Size(max = 2048)
        @URL(regexp = "^(?i)https?://.+", message = "originalUrl must be a well-formed HTTP or HTTPS URL")
        String originalUrl,
        @Future(message = "expiresAt must be in the future")
        Instant expiresAt,
        @Size(min = 4, max = 30, message = "customAlias must be between 4 and 30 characters")
        @Pattern(regexp = "^[a-z0-9_-]+$", message = "customAlias may only contain letters, digits, hyphens and underscores")
        String customAlias
) {
    public CreateShortUrlRequest {
        if (originalUrl != null) {
            originalUrl = originalUrl.trim();
        }
        if (customAlias != null) {
            customAlias = customAlias.trim();
            if (customAlias.isEmpty()) {
                customAlias = null;
            } else {
                customAlias = customAlias.toLowerCase(Locale.ROOT);
            }
        }
    }
}
