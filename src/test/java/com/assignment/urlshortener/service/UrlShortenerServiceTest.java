package com.assignment.urlshortener.service;

import com.assignment.urlshortener.cache.RedirectCache;
import com.assignment.urlshortener.dto.CreateShortUrlRequest;
import com.assignment.urlshortener.dto.CreateShortUrlResponse;
import com.assignment.urlshortener.dto.UrlAnalyticsResponse;
import com.assignment.urlshortener.entity.ShortUrl;
import com.assignment.urlshortener.exception.CustomAliasConflictException;
import com.assignment.urlshortener.exception.ShortCodeGenerationException;
import com.assignment.urlshortener.exception.ShortUrlAlreadyInactiveException;
import com.assignment.urlshortener.exception.ShortUrlExpiredException;
import com.assignment.urlshortener.exception.ShortUrlNotFoundException;
import com.assignment.urlshortener.repository.ShortUrlRepository;
import com.assignment.urlshortener.util.ShortCodeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

    @Mock
    private RedirectCache redirectCache;

    private UrlShortenerService urlShortenerService;

    @BeforeEach
    void setUp() {
        urlShortenerService =
                new UrlShortenerService(shortUrlRepository, shortCodeGenerator, redirectCache, "http://short.ly");
    }

    @Test
    void createShortUrlSucceedsOnFirstAttempt() {
        String originalUrl = "https://example.com/page";
        when(shortCodeGenerator.generate()).thenReturn("abc1234");
        when(shortUrlRepository.existsByShortCode("abc1234")).thenReturn(false);

        CreateShortUrlResponse response =
                urlShortenerService.createShortUrl(new CreateShortUrlRequest(originalUrl, null, null));

        assertThat(response.shortCode()).isEqualTo("abc1234");
        assertThat(response.originalUrl()).isEqualTo(originalUrl);
        assertThat(response.shortUrl()).isEqualTo("http://short.ly/abc1234");
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.expiresAt()).isNull();
        verify(shortUrlRepository).save(any(ShortUrl.class));
    }

    @Test
    void createShortUrlRetriesAfterCollision() {
        when(shortCodeGenerator.generate()).thenReturn("aaaaaaa", "bbbbbbb");
        when(shortUrlRepository.existsByShortCode("aaaaaaa")).thenReturn(true);
        when(shortUrlRepository.existsByShortCode("bbbbbbb")).thenReturn(false);

        CreateShortUrlResponse response =
                urlShortenerService.createShortUrl(new CreateShortUrlRequest("https://example.com/retry", null, null));

        assertThat(response.shortCode()).isEqualTo("bbbbbbb");
        verify(shortCodeGenerator, times(2)).generate();
        verify(shortUrlRepository).save(any(ShortUrl.class));
    }

    @Test
    void createShortUrlFailsAfterFiveCollisions() {
        when(shortCodeGenerator.generate()).thenReturn("ccccccc");
        when(shortUrlRepository.existsByShortCode("ccccccc")).thenReturn(true);

        assertThrows(ShortCodeGenerationException.class,
                () -> urlShortenerService.createShortUrl(
                        new CreateShortUrlRequest("https://example.com/fail", null, null)));

        verify(shortCodeGenerator, times(5)).generate();
        verify(shortUrlRepository, never()).save(any(ShortUrl.class));
    }

    @Test
    void createShortUrlStoresAndReturnsExpiresAt() {
        Instant expiresAt = Instant.now().plusSeconds(3600);
        when(shortCodeGenerator.generate()).thenReturn("exp1234");
        when(shortUrlRepository.existsByShortCode("exp1234")).thenReturn(false);

        CreateShortUrlResponse response = urlShortenerService.createShortUrl(
                new CreateShortUrlRequest("https://example.com/expiring", expiresAt, null));

        assertThat(response.expiresAt()).isEqualTo(expiresAt);
        verify(shortUrlRepository).save(any(ShortUrl.class));
    }

    @Test
    void createShortUrlUsesCustomAliasWhenProvided() {
        when(shortUrlRepository.existsByShortCode("my-alias")).thenReturn(false);

        CreateShortUrlResponse response = urlShortenerService.createShortUrl(
                new CreateShortUrlRequest("https://example.com/custom", null, "my-alias"));

        assertThat(response.shortCode()).isEqualTo("my-alias");
        assertThat(response.shortUrl()).isEqualTo("http://short.ly/my-alias");
        verify(shortCodeGenerator, never()).generate();
        verify(shortUrlRepository).save(any(ShortUrl.class));
    }

    @Test
    void createShortUrlThrowsConflictWhenCustomAliasAlreadyExists() {
        when(shortUrlRepository.existsByShortCode("taken")).thenReturn(true);

        assertThrows(CustomAliasConflictException.class, () -> urlShortenerService.createShortUrl(
                new CreateShortUrlRequest("https://example.com/dup", null, "taken")));

        verify(shortUrlRepository, never()).save(any(ShortUrl.class));
    }

    @Test
    void createShortUrlThrowsConflictWhenCustomAliasIsReserved() {
        assertThrows(CustomAliasConflictException.class, () -> urlShortenerService.createShortUrl(
                new CreateShortUrlRequest("https://example.com/reserved", null, "admin")));

        verify(shortUrlRepository, never()).existsByShortCode(any());
        verify(shortUrlRepository, never()).save(any(ShortUrl.class));
    }

    @Test
    void createShortUrlThrowsConflictWhenCustomAliasRaceLosesOnSave() {
        when(shortUrlRepository.existsByShortCode("race-alias")).thenReturn(false);
        when(shortUrlRepository.save(any(ShortUrl.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThrows(CustomAliasConflictException.class, () -> urlShortenerService.createShortUrl(
                new CreateShortUrlRequest("https://example.com/race", null, "race-alias")));
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
        Instant expiresAt = Instant.parse("2026-02-01T00:00:00Z");
        when(shortUrl.getShortCode()).thenReturn("abc1234");
        when(shortUrl.getOriginalUrl()).thenReturn("https://example.com/page");
        when(shortUrl.getClickCount()).thenReturn(5L);
        when(shortUrl.getCreatedAt()).thenReturn(createdAt);
        when(shortUrl.getLastAccessedAt()).thenReturn(lastAccessedAt);
        when(shortUrl.isActive()).thenReturn(true);
        when(shortUrl.getExpiresAt()).thenReturn(expiresAt);
        when(shortUrlRepository.findByShortCode("abc1234")).thenReturn(Optional.of(shortUrl));

        UrlAnalyticsResponse response = urlShortenerService.getAnalytics("abc1234");

        assertThat(response.shortCode()).isEqualTo("abc1234");
        assertThat(response.originalUrl()).isEqualTo("https://example.com/page");
        assertThat(response.clickCount()).isEqualTo(5L);
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.lastAccessedAt()).isEqualTo(lastAccessedAt);
        assertThat(response.active()).isTrue();
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void resolveOriginalUrlReturnsUrlForActiveNonExpiringShortCode() {
        ShortUrl shortUrl = new ShortUrl("https://example.com/page", "abc1234", Instant.now());
        when(redirectCache.getOriginalUrl("abc1234")).thenReturn(Optional.empty());
        when(shortUrlRepository.findByShortCode("abc1234")).thenReturn(Optional.of(shortUrl));

        String originalUrl = urlShortenerService.resolveOriginalUrl("abc1234");

        assertThat(originalUrl).isEqualTo("https://example.com/page");
        verify(shortUrlRepository).incrementClickCount(eq("abc1234"), any(Instant.class));
        verify(redirectCache).put(eq("abc1234"), eq("https://example.com/page"), isNull());
    }

    @Test
    void resolveOriginalUrlThrowsWhenShortCodeMissing() {
        when(redirectCache.getOriginalUrl("missing")).thenReturn(Optional.empty());
        when(shortUrlRepository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThrows(ShortUrlNotFoundException.class, () -> urlShortenerService.resolveOriginalUrl("missing"));

        verify(shortUrlRepository, never()).incrementClickCount(any(), any());
        verify(redirectCache, never()).put(any(), any(), any());
    }

    @Test
    void resolveOriginalUrlThrowsGoneAndDoesNotIncrementClickCountWhenExpired() {
        Instant expiresAt = Instant.now().minusSeconds(60);
        ShortUrl shortUrl = new ShortUrl("https://example.com/expired", "exp1234", Instant.now().minusSeconds(3600),
                expiresAt);
        when(redirectCache.getOriginalUrl("exp1234")).thenReturn(Optional.empty());
        when(shortUrlRepository.findByShortCode("exp1234")).thenReturn(Optional.of(shortUrl));

        assertThrows(ShortUrlExpiredException.class, () -> urlShortenerService.resolveOriginalUrl("exp1234"));

        verify(shortUrlRepository, never()).incrementClickCount(any(), any());
        verify(redirectCache, never()).put(any(), any(), any());
    }

    @Test
    void resolveOriginalUrlReturnsCachedUrlAndStillUpdatesAnalyticsOnCacheHit() {
        when(redirectCache.getOriginalUrl("hit1234")).thenReturn(Optional.of("https://example.com/hit"));
        when(shortUrlRepository.incrementClickCount(eq("hit1234"), any(Instant.class))).thenReturn(1);

        String originalUrl = urlShortenerService.resolveOriginalUrl("hit1234");

        assertThat(originalUrl).isEqualTo("https://example.com/hit");
        verify(shortUrlRepository).incrementClickCount(eq("hit1234"), any(Instant.class));
        verify(shortUrlRepository, never()).findByShortCode(any());
        verify(redirectCache, never()).evict(any());
        verify(redirectCache, never()).put(any(), any(), any());
    }

    @Test
    void resolveOriginalUrlEvictsStaleCacheAndFallsBackToRepositoryWhenUpdateAffectsZeroRows() {
        ShortUrl shortUrl = new ShortUrl("https://example.com/inactive", "stale123", Instant.now());
        shortUrl.deactivate();
        when(redirectCache.getOriginalUrl("stale123")).thenReturn(Optional.of("https://example.com/inactive"));
        when(shortUrlRepository.incrementClickCount(eq("stale123"), any(Instant.class))).thenReturn(0);
        when(shortUrlRepository.findByShortCode("stale123")).thenReturn(Optional.of(shortUrl));

        assertThrows(ShortUrlNotFoundException.class, () -> urlShortenerService.resolveOriginalUrl("stale123"));

        verify(redirectCache).evict("stale123");
        verify(shortUrlRepository, times(1)).incrementClickCount(eq("stale123"), any(Instant.class));
    }

    @Test
    void resolveOriginalUrlEvictsStaleCacheAndThrowsGoneWithoutRecachingWhenEntryExpired() {
        Instant expiresAt = Instant.now().minusSeconds(10);
        ShortUrl shortUrl = new ShortUrl("https://example.com/expired-stale", "stale456",
                Instant.now().minusSeconds(100), expiresAt);
        when(redirectCache.getOriginalUrl("stale456")).thenReturn(Optional.of("https://example.com/expired-stale"));
        when(shortUrlRepository.incrementClickCount(eq("stale456"), any(Instant.class))).thenReturn(0);
        when(shortUrlRepository.findByShortCode("stale456")).thenReturn(Optional.of(shortUrl));

        assertThrows(ShortUrlExpiredException.class, () -> urlShortenerService.resolveOriginalUrl("stale456"));

        verify(redirectCache).evict("stale456");
        verify(redirectCache, never()).put(any(), any(), any());
        verify(shortUrlRepository, times(1)).incrementClickCount(eq("stale456"), any(Instant.class));
    }

    @Test
    void deactivateShortUrlSucceedsForActiveShortCode() {
        ShortUrl shortUrl = new ShortUrl("https://example.com/page", "abc1234", Instant.now());
        when(shortUrlRepository.findByShortCode("abc1234")).thenReturn(Optional.of(shortUrl));

        urlShortenerService.deactivateShortUrl("abc1234");

        assertThat(shortUrl.isActive()).isFalse();
        verify(shortUrlRepository).save(shortUrl);
        verify(redirectCache).evict("abc1234");
    }

    @Test
    void deactivateShortUrlThrowsNotFoundWhenShortCodeMissing() {
        when(shortUrlRepository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThrows(ShortUrlNotFoundException.class,
                () -> urlShortenerService.deactivateShortUrl("missing"));

        verify(shortUrlRepository, never()).save(any());
        verify(redirectCache, never()).evict(any());
    }

    @Test
    void deactivateShortUrlThrowsWhenAlreadyInactive() {
        ShortUrl shortUrl = new ShortUrl("https://example.com/page", "inactive1", Instant.now());
        shortUrl.deactivate();
        when(shortUrlRepository.findByShortCode("inactive1")).thenReturn(Optional.of(shortUrl));

        assertThrows(ShortUrlAlreadyInactiveException.class,
                () -> urlShortenerService.deactivateShortUrl("inactive1"));

        verify(shortUrlRepository, never()).save(any());
        verify(redirectCache, never()).evict(any());
    }
}
