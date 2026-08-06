package com.assignment.urlshortener.service;

import com.assignment.urlshortener.entity.ClickEvent;
import com.assignment.urlshortener.repository.ClickEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClickEventRecorderTest {

    @Mock
    private ClickEventRepository clickEventRepository;

    private ClickEventRecorder clickEventRecorder;

    @BeforeEach
    void setUp() {
        clickEventRecorder = new ClickEventRecorder(clickEventRepository);
    }

    @Test
    void recordsClickEventWithKnownCountryAndBrowser() {
        Instant clickedAt = Instant.now();

        clickEventRecorder.recordClickEvent("abc1234", clickedAt, "IN",
                "Mozilla/5.0 Chrome/120.0.0.0 Safari/537.36");

        ArgumentCaptor<ClickEvent> captor = ArgumentCaptor.forClass(ClickEvent.class);
        verify(clickEventRepository).save(captor.capture());
        ClickEvent saved = captor.getValue();
        assertThat(saved.getShortCode()).isEqualTo("abc1234");
        assertThat(saved.getClickedAt()).isEqualTo(clickedAt);
        assertThat(saved.getCountry()).isEqualTo("IN");
        assertThat(saved.getBrowser()).isEqualTo("Chrome");
    }

    @Test
    void defaultsCountryToUnknownWhenHeaderMissing() {
        clickEventRecorder.recordClickEvent("abc1234", Instant.now(), null, "Firefox/121.0");

        ArgumentCaptor<ClickEvent> captor = ArgumentCaptor.forClass(ClickEvent.class);
        verify(clickEventRepository).save(captor.capture());
        assertThat(captor.getValue().getCountry()).isEqualTo("Unknown");
    }

    @Test
    void defaultsCountryToUnknownWhenHeaderBlank() {
        clickEventRecorder.recordClickEvent("abc1234", Instant.now(), "   ", "Firefox/121.0");

        ArgumentCaptor<ClickEvent> captor = ArgumentCaptor.forClass(ClickEvent.class);
        verify(clickEventRepository).save(captor.capture());
        assertThat(captor.getValue().getCountry()).isEqualTo("Unknown");
    }

    @Test
    void repositoryFailureDoesNotPropagate() {
        when(clickEventRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("database unavailable"));

        assertThatCode(() -> clickEventRecorder.recordClickEvent("abc1234", Instant.now(), "IN", "Firefox/121.0"))
                .doesNotThrowAnyException();
    }
}
