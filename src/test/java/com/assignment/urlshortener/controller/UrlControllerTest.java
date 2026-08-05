package com.assignment.urlshortener.controller;

import com.assignment.urlshortener.dto.CreateShortUrlResponse;
import com.assignment.urlshortener.exception.CustomAliasConflictException;
import com.assignment.urlshortener.exception.ShortUrlNotFoundException;
import com.assignment.urlshortener.service.UrlShortenerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
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
}
