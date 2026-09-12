package com.shortlink.dto.response;

import com.shortlink.entity.ShortUrl;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ShortenResponse {

    private final String shortCode;
    private final String shortUrl;
    private final String longUrl;
    private final long clickCount;
    private final LocalDateTime createdAt;
    private final LocalDateTime expiresAt;

    public ShortenResponse(ShortUrl entity, String baseUrl) {
        this.shortCode = entity.getShortCode();
        this.shortUrl = baseUrl + "/" + entity.getShortCode();
        this.longUrl = entity.getLongUrl();
        this.clickCount = entity.getClickCount();
        this.createdAt = entity.getCreatedAt();
        this.expiresAt = entity.getExpiresAt();
    }
}
