package com.assignment.urlshortener.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "click_events")
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String shortCode;

    @Column(nullable = false)
    private Instant clickedAt;

    @Column(nullable = false)
    private String country;

    @Column(nullable = false)
    private String browser;

    protected ClickEvent() {
        // required by JPA
    }

    public ClickEvent(String shortCode, Instant clickedAt, String country, String browser) {
        this.shortCode = shortCode;
        this.clickedAt = clickedAt;
        this.country = country;
        this.browser = browser;
    }

    public Long getId() {
        return id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public Instant getClickedAt() {
        return clickedAt;
    }

    public String getCountry() {
        return country;
    }

    public String getBrowser() {
        return browser;
    }
}
