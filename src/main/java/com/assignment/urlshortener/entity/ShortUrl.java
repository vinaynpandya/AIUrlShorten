package com.assignment.urlshortener.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

@Entity
@Table(name = "short_urls")
public class ShortUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Size(max = 2048)
    @Column(nullable = false, length = 2048)
    private String originalUrl;

    @NotNull
    @Column(nullable = false, unique = true)
    private String shortCode;

    @NotNull
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private long clickCount = 0L;

    private Instant lastAccessedAt;

    @Column(nullable = false)
    private boolean active = true;

    protected ShortUrl() {
        // required by JPA
    }

    public ShortUrl(String originalUrl, String shortCode, Instant createdAt) {
        this.originalUrl = originalUrl;
        this.shortCode = shortCode;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public String getShortCode() {
        return shortCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getClickCount() {
        return clickCount;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public boolean isActive() {
        return active;
    }

    public void recordAccess() {
        this.clickCount++;
        this.lastAccessedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
    }
}
