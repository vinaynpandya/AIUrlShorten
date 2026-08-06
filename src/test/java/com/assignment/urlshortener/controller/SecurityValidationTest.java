package com.assignment.urlshortener.controller;

import com.assignment.urlshortener.dto.ClickAnalyticsResponse;
import com.assignment.urlshortener.dto.CreateShortUrlRequest;
import com.assignment.urlshortener.dto.CreateShortUrlResponse;
import com.assignment.urlshortener.service.ClickAnalyticsService;
import com.assignment.urlshortener.service.UrlShortenerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Focused security regression tests: SQL-injection-style input, unsafe URL
 * schemes, XSS-style input, and API error responses that must not leak
 * internal implementation details.
 */
@WebMvcTest(UrlController.class)
class SecurityValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UrlShortenerService urlShortenerService;

    @MockitoBean
    private ClickAnalyticsService clickAnalyticsService;

    // ---- 1. SQL-injection-style input ----

    @Test
    void createShortUrlWithSqlInjectionStyleCustomAliasReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateShortUrlRequest(
                                "https://example.com/page", null, "1;DROP TABLE short_urls;--"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.customAlias").exists());
    }

    @Test
    void createShortUrlWithSqlInjectionStyleOriginalUrlIsAcceptedAsLiteralText() throws Exception {
        String maliciousUrl = "https://example.com/search?id=1'OR'1'='1';DROP;--";
        when(urlShortenerService.createShortUrl(any())).thenReturn(
                new CreateShortUrlResponse("abc1234", "http://short.ly/abc1234", maliciousUrl, Instant.now(), null));

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateShortUrlRequest(maliciousUrl, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalUrl").value(maliciousUrl));
    }

    @Test
    void unexpectedServiceFailureDoesNotLeakInternalDetails() throws Exception {
        when(urlShortenerService.createShortUrl(any())).thenThrow(
                new RuntimeException("org.h2.jdbc.JdbcSQLSyntaxErrorException: "
                        + "syntax error in SQL statement \"DROP TABLE short_urls\" at line 1"));

        MvcResult result = mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateShortUrlRequest("https://example.com/page", null, null))))
                .andExpect(status().isInternalServerError())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContainIgnoringCase("sql");
        assertThat(body).doesNotContain("org.h2", "JdbcSQLSyntaxErrorException", "DROP TABLE",
                "at com.assignment", "Caused by");
        assertThat(body).contains("An unexpected error occurred");
    }

    // ---- 2. Unsafe URL schemes ----

    @Test
    void createShortUrlWithJavascriptSchemeReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateShortUrlRequest("javascript:alert(1)", null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createShortUrlWithDataSchemeReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateShortUrlRequest("data:text/html,<script>alert(1)</script>", null, null))))
                .andExpect(status().isBadRequest());
    }

    // ---- 3. XSS-style input ----

    @Test
    void createShortUrlWithScriptTagCustomAliasReturnsBadRequest() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateShortUrlRequest(
                                "https://example.com/page", null, "<script>alert(1)</script>"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.customAlias").exists())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("<script>");
    }

    @Test
    void createShortUrlWithImgOnErrorCustomAliasReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateShortUrlRequest(
                                "https://example.com/page", null, "imgsrc=xonerror=alert(1)"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.customAlias").exists());
    }

    @Test
    void createShortUrlWithQuotesAndEventAttributeCustomAliasReturnsBadRequest() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateShortUrlRequest(
                                "https://example.com/page", null, "\"><script>alert(1)</script>"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.customAlias").exists())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("<script>");
    }

    // ---- 5. Existing validation (gaps not yet covered elsewhere) ----

    @Test
    void createShortUrlWithMalformedUrlReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateShortUrlRequest("not-a-valid-url", null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.originalUrl").exists());
    }

    @Test
    void createShortUrlWithOriginalUrlExceedingMaxLengthReturnsBadRequest() throws Exception {
        String tooLongUrl = "https://example.com/" + "a".repeat(2048);

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateShortUrlRequest(tooLongUrl, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.originalUrl").exists());
    }
}
