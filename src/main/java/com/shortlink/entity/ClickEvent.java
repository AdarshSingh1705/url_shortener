package com.shortlink.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "click_events")
@Getter
@Setter
@NoArgsConstructor
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "short_url_id", nullable = false)
    private ShortUrl shortUrl;

    @Column(name = "clicked_at", nullable = false)
    private LocalDateTime clickedAt;

    @Column(name = "referrer", length = 512)
    private String referrer;

    // SHA-256 hash of the client IP, never the raw IP — enough to dedupe/analyze without storing PII.
    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    public ClickEvent(ShortUrl shortUrl, String referrer, String ipHash) {
        this.shortUrl = shortUrl;
        this.referrer = referrer;
        this.ipHash = ipHash;
        this.clickedAt = LocalDateTime.now();
    }
}
