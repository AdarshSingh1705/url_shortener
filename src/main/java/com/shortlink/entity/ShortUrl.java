package com.shortlink.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "short_urls")
@Getter
@Setter
@NoArgsConstructor
public class ShortUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Null until assignCode() is called after the row has an id (needed to base62-encode the id).
    @Column(name = "short_code", unique = true, length = 20)
    private String shortCode;

    @Column(name = "long_url", nullable = false, length = 2048)
    private String longUrl;

    @Column(name = "click_count", nullable = false)
    private long clickCount = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}
