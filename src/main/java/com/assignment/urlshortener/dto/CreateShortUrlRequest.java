package com.assignment.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record CreateShortUrlRequest(
        @NotBlank
        @Size(max = 2048)
        @URL(regexp = "^(?i)https?://.+", message = "originalUrl must be a well-formed HTTP or HTTPS URL")
        String originalUrl
) {
    public CreateShortUrlRequest {
        if (originalUrl != null) {
            originalUrl = originalUrl.trim();
        }
    }
}
