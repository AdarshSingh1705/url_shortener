package com.shortlink.service;

import com.shortlink.dto.request.ShortenRequest;
import com.shortlink.dto.response.ShortenResponse;
import com.shortlink.entity.ClickEvent;
import com.shortlink.entity.ShortUrl;
import com.shortlink.exception.NotFoundException;
import com.shortlink.repository.ClickEventRepository;
import com.shortlink.repository.ShortUrlRepository;
import com.shortlink.util.Base62Encoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class UrlService {

    private static final Logger logger = LoggerFactory.getLogger(UrlService.class);
    private static final String CACHE_PREFIX = "shorturl:";

    private final ShortUrlRepository shortUrlRepository;
    private final ClickEventRepository clickEventRepository;
    private final StringRedisTemplate redisTemplate;
    private final String baseUrl;
    private final Duration cacheTtl;

    public UrlService(ShortUrlRepository shortUrlRepository,
                       ClickEventRepository clickEventRepository,
                       StringRedisTemplate redisTemplate,
                       @Value("${app.shortener.base-url}") String baseUrl,
                       @Value("${app.shortener.cache-ttl-hours:24}") long cacheTtlHours) {
        this.shortUrlRepository = shortUrlRepository;
        this.clickEventRepository = clickEventRepository;
        this.redisTemplate = redisTemplate;
        this.baseUrl = baseUrl;
        this.cacheTtl = Duration.ofHours(cacheTtlHours);
    }

    @Transactional
    public ShortenResponse shorten(ShortenRequest request) {
        ShortUrl entity = new ShortUrl();
        entity.setLongUrl(request.getLongUrl());
        if (request.getExpiresInDays() != null) {
            entity.setExpiresAt(LocalDateTime.now().plusDays(request.getExpiresInDays()));
        }

        // Save first so the DB assigns an id, then derive the short code from that id.
        // This guarantees uniqueness by construction — no collision retries needed.
        entity = shortUrlRepository.save(entity);
        entity.setShortCode(Base62Encoder.encode(entity.getId()));
        entity = shortUrlRepository.save(entity);

        cachePut(entity.getShortCode(), entity.getLongUrl());

        return new ShortenResponse(entity, baseUrl);
    }

    /**
     * Cache-aside lookup: check Redis first; on a miss, fall back to Postgres,
     * populate the cache for next time, and record the click.
     */
    @Transactional
    public String resolveAndRecordClick(String shortCode, String referrer, String clientIp) {
        String cachedUrl = redisTemplate.opsForValue().get(CACHE_PREFIX + shortCode);

        ShortUrl entity;
        if (cachedUrl != null) {
            entity = shortUrlRepository.findByShortCode(shortCode)
                    .orElseThrow(() -> new NotFoundException("Short link not found: " + shortCode));
        } else {
            logger.debug("Cache miss for {}", shortCode);
            entity = shortUrlRepository.findByShortCode(shortCode)
                    .orElseThrow(() -> new NotFoundException("Short link not found: " + shortCode));
            cachePut(shortCode, entity.getLongUrl());
        }

        if (entity.isExpired()) {
            redisTemplate.delete(CACHE_PREFIX + shortCode);
            throw new NotFoundException("Short link has expired: " + shortCode);
        }

        recordClick(entity, referrer, clientIp);
        return entity.getLongUrl();
    }

    private void recordClick(ShortUrl entity, String referrer, String clientIp) {
        entity.setClickCount(entity.getClickCount() + 1);
        shortUrlRepository.save(entity);

        ClickEvent event = new ClickEvent(entity, referrer, hashIp(clientIp));
        clickEventRepository.save(event);
    }

    private void cachePut(String shortCode, String longUrl) {
        redisTemplate.opsForValue().set(CACHE_PREFIX + shortCode, longUrl, cacheTtl);
    }

    private String hashIp(String ip) {
        if (ip == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(ip.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available on the standard JDK provider; this is unreachable in practice.
            return null;
        }
    }
}
