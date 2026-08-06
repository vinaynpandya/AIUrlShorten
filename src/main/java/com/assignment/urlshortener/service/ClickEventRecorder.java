package com.assignment.urlshortener.service;

import com.assignment.urlshortener.entity.ClickEvent;
import com.assignment.urlshortener.repository.ClickEventRepository;
import com.assignment.urlshortener.util.BrowserDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ClickEventRecorder {

    private static final Logger log = LoggerFactory.getLogger(ClickEventRecorder.class);

    private final ClickEventRepository clickEventRepository;

    public ClickEventRecorder(ClickEventRepository clickEventRepository) {
        this.clickEventRepository = clickEventRepository;
    }

    @Async
    @Transactional
    public void recordClickEvent(String shortCode, Instant clickedAt, String countryHeaderValue, String userAgent) {
        try {
            String country = (countryHeaderValue == null || countryHeaderValue.isBlank())
                    ? "Unknown"
                    : countryHeaderValue;
            String browser = BrowserDetector.detect(userAgent);
            clickEventRepository.save(new ClickEvent(shortCode, clickedAt, country, browser));
        } catch (Exception ex) {
            log.warn("Failed to persist click event for {}: {}", shortCode, ex.getMessage());
        }
    }
}
