package com.assignment.urlshortener.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BrowserDetectorTest {

    @Test
    void detectsChrome() {
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

        assertThat(BrowserDetector.detect(userAgent)).isEqualTo("Chrome");
    }

    @Test
    void detectsFirefox() {
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/121.0";

        assertThat(BrowserDetector.detect(userAgent)).isEqualTo("Firefox");
    }

    @Test
    void detectsSafari() {
        String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 "
                + "(KHTML, like Gecko) Version/17.1 Safari/605.1.15";

        assertThat(BrowserDetector.detect(userAgent)).isEqualTo("Safari");
    }

    @Test
    void detectsEdgeBeforeChrome() {
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0";

        assertThat(BrowserDetector.detect(userAgent)).isEqualTo("Edge");
    }

    @Test
    void detectsOperaBeforeChrome() {
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 OPR/106.0.0.0";

        assertThat(BrowserDetector.detect(userAgent)).isEqualTo("Opera");
    }

    @Test
    void returnsUnknownForUnrecognizedUserAgent() {
        assertThat(BrowserDetector.detect("SomeCustomBot/1.0")).isEqualTo("Unknown");
    }

    @Test
    void returnsUnknownForNullUserAgent() {
        assertThat(BrowserDetector.detect(null)).isEqualTo("Unknown");
    }

    @Test
    void returnsUnknownForBlankUserAgent() {
        assertThat(BrowserDetector.detect("   ")).isEqualTo("Unknown");
    }
}
