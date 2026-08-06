package com.assignment.urlshortener.service;

import com.assignment.urlshortener.dto.ClickAnalyticsResponse;
import com.assignment.urlshortener.exception.ShortUrlNotFoundException;
import com.assignment.urlshortener.repository.ClickEventRepository;
import com.assignment.urlshortener.repository.ClickEventRepository.GroupCount;
import com.assignment.urlshortener.repository.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClickAnalyticsServiceTest {

    @Mock
    private ShortUrlRepository shortUrlRepository;

    @Mock
    private ClickEventRepository clickEventRepository;

    private ClickAnalyticsService clickAnalyticsService;

    @BeforeEach
    void setUp() {
        clickAnalyticsService = new ClickAnalyticsService(shortUrlRepository, clickEventRepository);
    }

    @Test
    void returnsBreakdownForExistingShortCode() {
        when(shortUrlRepository.existsByShortCode("abc1234")).thenReturn(true);
        when(clickEventRepository.countByShortCode("abc1234")).thenReturn(3L);
        when(clickEventRepository.countByBrowserForShortCode("abc1234"))
                .thenReturn(List.of(groupCount("Chrome", 3L)));

        ClickAnalyticsResponse response = clickAnalyticsService.getClickAnalytics("abc1234");

        assertThat(response.shortCode()).isEqualTo("abc1234");
        assertThat(response.totalEvents()).isEqualTo(3L);
        assertThat(response.byBrowser()).containsEntry("Chrome", 3L);
    }

    @Test
    void throwsNotFoundForUnknownShortCode() {
        when(shortUrlRepository.existsByShortCode("missing")).thenReturn(false);

        assertThatThrownBy(() -> clickAnalyticsService.getClickAnalytics("missing"))
                .isInstanceOf(ShortUrlNotFoundException.class);
    }

    private GroupCount groupCount(String name, long total) {
        return new GroupCount() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
