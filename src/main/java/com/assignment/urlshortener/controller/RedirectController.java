package com.assignment.urlshortener.controller;

import com.assignment.urlshortener.service.ClickEventRecorder;
import com.assignment.urlshortener.service.UrlShortenerService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;

@RestController
public class RedirectController {

    private static final String COUNTRY_HEADER = "CF-IPCountry";

    private final UrlShortenerService urlShortenerService;
    private final ClickEventRecorder clickEventRecorder;

    public RedirectController(UrlShortenerService urlShortenerService, ClickEventRecorder clickEventRecorder) {
        this.urlShortenerService = urlShortenerService;
        this.clickEventRecorder = clickEventRecorder;
    }

    @GetMapping("/{shortCode:[a-zA-Z0-9_-]+}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode, HttpServletRequest request) {
        String originalUrl = urlShortenerService.resolveOriginalUrl(shortCode);

        clickEventRecorder.recordClickEvent(shortCode, Instant.now(),
                request.getHeader(COUNTRY_HEADER), request.getHeader("User-Agent"));

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(originalUrl))
                .build();
    }
}
