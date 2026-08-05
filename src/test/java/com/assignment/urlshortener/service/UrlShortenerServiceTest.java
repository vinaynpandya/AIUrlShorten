package com.assignment.urlshortener.service;

import com.assignment.urlshortener.dto.CreateShortUrlRequest;
import com.assignment.urlshortener.dto.CreateShortUrlResponse;
import com.assignment.urlshortener.dto.UrlAnalyticsResponse;
import com.assignment.urlshortener.entity.ShortUrl;
import com.assignment.urlshortener.exception.ShortCodeGenerationException;
import com.assignment.urlshortener.exception.ShortUrlNotFoundException;
import com.assignment.urlshortener.repository.ShortUrlRepository;
import com.assignment.urlshortener.util.ShortCodeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrlShortenerServiceTest {

    @Mock
    private ShortUrlRepository shortUrlRepository;

    @Mock
    private ShortCodeGenerator shortCodeGenerator;

    private UrlShortenerService urlShortenerService;

    @BeforeEach
    void setUp() {
        urlShortenerService = new UrlShortenerService(shortUrlRepository, shortCodeGenerator, "http://short.ly");
    }

    @Test
    void createShortUrlSucceedsOnFirstAttempt() {
        String originalUrl = "https://example.com/page";
        when(shortCodeGenerator.generate()).thenReturn("abc1234");
        when(shortUrlRepository.existsByShortCode("abc1234")).thenReturn(false);

        CreateShortUrlResponse response = urlShortenerService.createShortUrl(new CreateShortUrlRequest(originalUrl));

        assertThat(response.shortCode()).isEqualTo("abc1234");
        assertThat(response.originalUrl()).isEqualTo(originalUrl);
        assertThat(response.shortUrl()).isEqualTo("http://short.ly/abc1234");
        assertThat(response.createdAt()).isNotNull();
        verify(shortUrlRepository).save(any(ShortUrl.class));
    }

    @Test
    void createShortUrlRetriesAfterCollision() {
        when(shortCodeGenerator.generate()).thenReturn("aaaaaaa", "bbbbbbb");
        when(shortUrlRepository.existsByShortCode("aaaaaaa")).thenReturn(true);
        when(shortUrlRepository.existsByShortCode("bbbbbbb")).thenReturn(false);

        CreateShortUrlResponse response =
                urlShortenerService.createShortUrl(new CreateShortUrlRequest("https://example.com/retry"));

        assertThat(response.shortCode()).isEqualTo("bbbbbbb");
        verify(shortCodeGenerator, times(2)).generate();
        verify(shortUrlRepository).save(any(ShortUrl.class));
    }

    @Test
    void createShortUrlFailsAfterFiveCollisions() {
        when(shortCodeGenerator.generate()).thenReturn("ccccccc");
        when(shortUrlRepository.existsByShortCode("ccccccc")).thenReturn(true);

        assertThrows(ShortCodeGenerationException.class,
                () -> urlShortenerService.createShortUrl(new CreateShortUrlRequest("https://example.com/fail")));

        verify(shortCodeGenerator, times(5)).generate();
        verify(shortUrlRepository, never()).save(any(ShortUrl.class));
    }

    @Test
    void getAnalyticsThrowsWhenShortCodeUnknown() {
        when(shortUrlRepository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThrows(ShortUrlNotFoundException.class, () -> urlShortenerService.getAnalytics("missing"));
    }

    @Test
    void getAnalyticsMapsShortUrlFields() {
        ShortUrl shortUrl = mock(ShortUrl.class);
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant lastAccessedAt = Instant.parse("2026-01-02T00:00:00Z");
        when(shortUrl.getShortCode()).thenReturn("abc1234");
        when(shortUrl.getOriginalUrl()).thenReturn("https://example.com/page");
        when(shortUrl.getClickCount()).thenReturn(5L);
        when(shortUrl.getCreatedAt()).thenReturn(createdAt);
        when(shortUrl.getLastAccessedAt()).thenReturn(lastAccessedAt);
        when(shortUrl.isActive()).thenReturn(true);
        when(shortUrlRepository.findByShortCode("abc1234")).thenReturn(Optional.of(shortUrl));

        UrlAnalyticsResponse response = urlShortenerService.getAnalytics("abc1234");

        assertThat(response.shortCode()).isEqualTo("abc1234");
        assertThat(response.originalUrl()).isEqualTo("https://example.com/page");
        assertThat(response.clickCount()).isEqualTo(5L);
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.lastAccessedAt()).isEqualTo(lastAccessedAt);
        assertThat(response.active()).isTrue();
    }
}
