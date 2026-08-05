package com.assignment.urlshortener.integration;

import com.assignment.urlshortener.dto.CreateShortUrlRequest;
import com.assignment.urlshortener.dto.CreateShortUrlResponse;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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

    @Test
    void createShortUrlThenRedirectTracksClickCount() throws Exception {
        String originalUrl = "https://example.com/integration-test-page";

        MvcResult createResult = mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateShortUrlRequest(originalUrl))))
                .andExpect(status().isCreated())
                .andReturn();

        CreateShortUrlResponse createResponse = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), CreateShortUrlResponse.class);
        String shortCode = createResponse.shortCode();

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
}
