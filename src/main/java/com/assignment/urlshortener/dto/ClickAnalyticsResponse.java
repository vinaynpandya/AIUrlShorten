package com.assignment.urlshortener.dto;

import java.util.Map;

public record ClickAnalyticsResponse(
        String shortCode,
        long totalEvents,
        Map<String, Long> byBrowser
) {
}
