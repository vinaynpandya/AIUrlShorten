package com.assignment.urlshortener.controller;

import com.assignment.urlshortener.exception.ShortUrlExpiredException;
import com.assignment.urlshortener.exception.ShortUrlNotFoundException;
import com.assignment.urlshortener.service.ClickEventRecorder;
import com.assignment.urlshortener.service.UrlShortenerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RedirectController.class)
class RedirectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UrlShortenerService urlShortenerService;

    @MockitoBean
    private ClickEventRecorder clickEventRecorder;

    @Test
    void successfulRedirectRecordsClickEventWithCountryAndUserAgent() throws Exception {
        when(urlShortenerService.resolveOriginalUrl("abc1234")).thenReturn("https://example.com/page");

        mockMvc.perform(get("/{shortCode}", "abc1234")
                        .header("CF-IPCountry", "IN")
                        .header("User-Agent", "Mozilla/5.0 Chrome/120.0.0.0 Safari/537.36"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/page"));

        verify(clickEventRecorder).recordClickEvent(eq("abc1234"), any(Instant.class), eq("IN"),
                eq("Mozilla/5.0 Chrome/120.0.0.0 Safari/537.36"));
    }

    @Test
    void successfulRedirectWithoutCountryHeaderPassesNullThrough() throws Exception {
        when(urlShortenerService.resolveOriginalUrl("abc1234")).thenReturn("https://example.com/page");

        mockMvc.perform(get("/{shortCode}", "abc1234")
                        .header("User-Agent", "Firefox/121.0"))
                .andExpect(status().isFound());

        verify(clickEventRecorder).recordClickEvent(eq("abc1234"), any(Instant.class), isNull(),
                eq("Firefox/121.0"));
    }

    @Test
    void unknownShortCodeReturnsNotFoundAndDoesNotRecordClickEvent() throws Exception {
        when(urlShortenerService.resolveOriginalUrl("missing"))
                .thenThrow(new ShortUrlNotFoundException("missing"));

        mockMvc.perform(get("/{shortCode}", "missing"))
                .andExpect(status().isNotFound());

        verify(clickEventRecorder, never()).recordClickEvent(anyString(), any(Instant.class), anyString(),
                anyString());
    }

    @Test
    void expiredShortCodeReturnsGoneAndDoesNotRecordClickEvent() throws Exception {
        when(urlShortenerService.resolveOriginalUrl("expired"))
                .thenThrow(new ShortUrlExpiredException("expired"));

        mockMvc.perform(get("/{shortCode}", "expired"))
                .andExpect(status().isGone());

        verify(clickEventRecorder, never()).recordClickEvent(anyString(), any(Instant.class), anyString(),
                anyString());
    }
}
