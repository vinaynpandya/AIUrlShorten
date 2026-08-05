package com.assignment.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateShortUrlRequest(
        @NotBlank
        @Size(max = 2048)
        String originalUrl
) {
}
