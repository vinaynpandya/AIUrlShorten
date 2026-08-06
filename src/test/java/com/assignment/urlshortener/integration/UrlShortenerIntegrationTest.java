package com.assignment.urlshortener.integration;

import com.assignment.urlshortener.dto.CreateShortUrlRequest;
import com.assignment.urlshortener.dto.CreateShortUrlResponse;
import com.assignment.urlshortener.entity.ShortUrl;
import com.assignment.urlshortener.repository.ShortUrlRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class UrlShortenerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    @Test
    void createShortUrlThenRedirectTracksClickCount() throws Exception {
        String originalUrl = "https://example.com/integration-test-page";

        MvcResult createResult = mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateShortUrlRequest(originalUrl, null, null))))
                .andExpect(status().isCreated())
                .andReturn();

        CreateShortUrlResponse createResponse = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), CreateShortUrlResponse.class);
        String shortCode = createResponse.shortCode();
        assertThat(createResponse.expiresAt()).isNull();

        mockMvc.perform(get("/{shortCode}", shortCode))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, originalUrl));

        mockMvc.perform(get("/api/v1/urls/{shortCode}/analytics", shortCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickCount").value(1));

        mockMvc.perform(get("/{shortCode}", shortCode))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, originalUrl));

        mockMvc.perform(get("/api/v1/urls/{shortCode}/analytics", shortCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickCount").value(2));
    }

    @Test
    void redirectWithUnknownShortCodeReturnsNotFound() throws Exception {
        mockMvc.perform(get("/{shortCode}", "zzzzzzz"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createShortUrlWithFutureExpiresAtIsAcceptedAndReturned() throws Exception {
        Instant expiresAt = Instant.now().plusSeconds(3600);

        MvcResult createResult = mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateShortUrlRequest("https://example.com/expiring-page", expiresAt, null))))
                .andExpect(status().isCreated())
                .andReturn();

        CreateShortUrlResponse createResponse = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), CreateShortUrlResponse.class);

        assertThat(createResponse.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void createShortUrlWithPastExpiresAtIsRejected() throws Exception {
        Instant expiresAt = Instant.now().minusSeconds(60);

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateShortUrlRequest("https://example.com/already-expired", expiresAt, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createShortUrlWithCustomAliasIsNormalizedAndRedirects() throws Exception {
        String originalUrl = "https://example.com/custom-alias-page";

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateShortUrlRequest(originalUrl, null, "My-Custom-Alias"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("my-custom-alias"));

        mockMvc.perform(get("/{shortCode}", "my-custom-alias"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, originalUrl));
    }

    @Test
    void createShortUrlWithDuplicateCustomAliasReturnsConflict() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateShortUrlRequest("https://example.com/first", null, "duplicate-alias"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateShortUrlRequest("https://example.com/second", null, "duplicate-alias"))))
                .andExpect(status().isConflict());
    }

    @Test
    void createShortUrlWithReservedCustomAliasReturnsConflict() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateShortUrlRequest("https://example.com/reserved", null, "admin"))))
                .andExpect(status().isConflict());
    }

    @Test
    void createShortUrlWithCustomAliasMatchingDeactivatedAliasReturnsConflict() throws Exception {
        ShortUrl deactivated = new ShortUrl("https://example.com/old", "old-alias", Instant.now());
        deactivated.deactivate();
        shortUrlRepository.save(deactivated);

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateShortUrlRequest("https://example.com/new", null, "old-alias"))))
                .andExpect(status().isConflict());
    }

    @Test
    void redirectWithExpiredShortCodeReturnsGoneAndDoesNotIncrementClickCount() throws Exception {
        ShortUrl expiredShortUrl = new ShortUrl("https://example.com/expired-page", "exp0001",
                Instant.now().minusSeconds(3600), Instant.now().minusSeconds(60));
        shortUrlRepository.save(expiredShortUrl);

        mockMvc.perform(get("/{shortCode}", "exp0001"))
                .andExpect(status().isGone());

        mockMvc.perform(get("/api/v1/urls/{shortCode}/analytics", "exp0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickCount").value(0))
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    void createShortUrlWithSqlInjectionStyleOriginalUrlPersistsLiterallyAndDoesNotAffectOtherRecords()
            throws Exception {
        String maliciousUrl = "https://example.com/search?id=1'OR'1'='1';DROP;--";
        long countBefore = shortUrlRepository.count();

        MvcResult createResult = mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateShortUrlRequest(maliciousUrl, null, null))))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(shortUrlRepository.count()).isEqualTo(countBefore + 1);

        CreateShortUrlResponse createResponse = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), CreateShortUrlResponse.class);
        String shortCode = createResponse.shortCode();

        ShortUrl stored = shortUrlRepository.findByShortCode(shortCode).orElseThrow();
        assertThat(stored.getOriginalUrl()).isEqualTo(maliciousUrl);

        mockMvc.perform(get("/{shortCode}", shortCode))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, maliciousUrl));

        assertThat(shortUrlRepository.count()).isEqualTo(countBefore + 1);
    }

    @Test
    void createShortUrlWithSqlInjectionStyleCustomAliasIsRejectedAndDoesNotAffectDatabase() throws Exception {
        long countBefore = shortUrlRepository.count();

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateShortUrlRequest(
                                "https://example.com/page", null, "1;DROP TABLE short_urls;--"))))
                .andExpect(status().isBadRequest());

        assertThat(shortUrlRepository.count()).isEqualTo(countBefore);
    }

    @Test
    void redirectWithSqlInjectionStyleShortCodePathReturnsNotFoundWithoutAffectingDatabase() throws Exception {
        long countBefore = shortUrlRepository.count();

        MvcResult result = mockMvc.perform(get("/{shortCode}", "1' OR '1'='1"))
                .andExpect(status().isNotFound())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContainIgnoringCase("sql");
        assertThat(body).doesNotContain("org.h2", "org.hibernate", "at com.assignment", "Caused by");
        assertThat(shortUrlRepository.count()).isEqualTo(countBefore);
    }
}
