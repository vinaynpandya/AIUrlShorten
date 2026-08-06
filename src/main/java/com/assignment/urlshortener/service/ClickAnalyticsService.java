package com.assignment.urlshortener.service;

import com.assignment.urlshortener.dto.ClickAnalyticsResponse;
import com.assignment.urlshortener.exception.ShortUrlNotFoundException;
import com.assignment.urlshortener.repository.ClickEventRepository;
import com.assignment.urlshortener.repository.ClickEventRepository.GroupCount;
import com.assignment.urlshortener.repository.ShortUrlRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ClickAnalyticsService {

    private final ShortUrlRepository shortUrlRepository;
    private final ClickEventRepository clickEventRepository;

    public ClickAnalyticsService(ShortUrlRepository shortUrlRepository, ClickEventRepository clickEventRepository) {
        this.shortUrlRepository = shortUrlRepository;
        this.clickEventRepository = clickEventRepository;
    }

    @Transactional(readOnly = true)
    public ClickAnalyticsResponse getClickAnalytics(String shortCode) {
        if (!shortUrlRepository.existsByShortCode(shortCode)) {
            throw new ShortUrlNotFoundException(shortCode);
        }

        long totalEvents = clickEventRepository.countByShortCode(shortCode);
        Map<String, Long> byBrowser = toMap(clickEventRepository.countByBrowserForShortCode(shortCode));

        return new ClickAnalyticsResponse(shortCode, totalEvents, byBrowser);
    }

    private Map<String, Long> toMap(List<GroupCount> groupCounts) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (GroupCount groupCount : groupCounts) {
            result.put(groupCount.getName(), groupCount.getTotal());
        }
        return result;
    }
}
