package com.assignment.urlshortener.controller;

import com.assignment.urlshortener.dto.ClickAnalyticsResponse;
import com.assignment.urlshortener.dto.CreateShortUrlResponse;
import com.assignment.urlshortener.exception.CustomAliasConflictException;
import com.assignment.urlshortener.exception.ShortUrlNotFoundException;
import com.assignment.urlshortener.service.ClickAnalyticsService;
import com.assignment.urlshortener.service.UrlShortenerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UrlController.class)
class UrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UrlShortenerService urlShortenerService;

    @MockitoBean
    private ClickAnalyticsService clickAnalyticsService;

    @Test
    void createShortUrlWithValidRequestReturnsCreated() throws Exception {
        when(urlShortenerService.createShortUrl(any())).thenReturn(
                new CreateShortUrlResponse("abc1234", "http://short.ly/abc1234", "https://example.com/page", Instant.now(), null));

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com/page\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("abc1234"));
    }

    @Test
    void createShortUrlWithBlankOriginalUrlReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createShortUrlWithFtpUrlReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"ftp://example.com/file\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createShortUrlWithTooShortCustomAliasReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com/page\",\"customAlias\":\"ab\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createShortUrlWithInvalidCustomAliasCharactersReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com/page\",\"customAlias\":\"bad alias!\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createShortUrlWithDuplicateCustomAliasReturnsConflict() throws Exception {
        when(urlShortenerService.createShortUrl(any())).thenThrow(new CustomAliasConflictException("taken"));

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com/page\",\"customAlias\":\"taken\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void getAnalyticsWithUnknownShortCodeReturnsNotFound() throws Exception {
        when(urlShortenerService.getAnalytics(anyString())).thenThrow(new ShortUrlNotFoundException("missing"));

        mockMvc.perform(get("/api/v1/urls/{shortCode}/analytics", "missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createShortUrlWithMalformedExpiresAtReturnsBadRequestNotServerError() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com/page\",\"expiresAt\":\"not-a-date\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.expiresAt").exists());
    }

    @Test
    void getClickAnalyticsReturnsBreakdown() throws Exception {
        when(clickAnalyticsService.getClickAnalytics("abc1234")).thenReturn(
                new ClickAnalyticsResponse("abc1234", 3L, Map.of("Chrome", 3L)));

        mockMvc.perform(get("/api/v1/urls/{shortCode}/click-analytics", "abc1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents").value(3))
                .andExpect(jsonPath("$.byBrowser.Chrome").value(3));
    }

    @Test
    void getClickAnalyticsWithUnknownShortCodeReturnsNotFound() throws Exception {
        when(clickAnalyticsService.getClickAnalytics("missing")).thenThrow(new ShortUrlNotFoundException("missing"));

        mockMvc.perform(get("/api/v1/urls/{shortCode}/click-analytics", "missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deactivateShortUrlReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/urls/{shortCode}", "abc1234"))
                .andExpect(status().isNoContent());

        verify(urlShortenerService).deactivateShortUrl("abc1234");
    }

    @Test
    void deactivateShortUrlWithUnknownShortCodeReturnsNotFound() throws Exception {
        doThrow(new ShortUrlNotFoundException("missing")).when(urlShortenerService).deactivateShortUrl("missing");

        mockMvc.perform(delete("/api/v1/urls/{shortCode}", "missing"))
                .andExpect(status().isNotFound());
    }
}
