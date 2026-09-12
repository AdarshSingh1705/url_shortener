package com.shortlink.service;

import com.shortlink.dto.response.AnalyticsResponse;
import com.shortlink.entity.ShortUrl;
import com.shortlink.exception.NotFoundException;
import com.shortlink.repository.ClickEventRepository;
import com.shortlink.repository.ShortUrlRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final ShortUrlRepository shortUrlRepository;
    private final ClickEventRepository clickEventRepository;

    public AnalyticsService(ShortUrlRepository shortUrlRepository, ClickEventRepository clickEventRepository) {
        this.shortUrlRepository = shortUrlRepository;
        this.clickEventRepository = clickEventRepository;
    }

    public AnalyticsResponse getAnalytics(String shortCode) {
        ShortUrl entity = shortUrlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new NotFoundException("Short link not found: " + shortCode));

        List<Map<String, Object>> byDay = clickEventRepository.countClicksByDay(shortCode).stream()
                .map(row -> row(row[0], "date", row[1]))
                .collect(Collectors.toList());

        List<Map<String, Object>> byReferrer = clickEventRepository.countClicksByReferrer(shortCode).stream()
                .map(row -> row(row[0], "referrer", row[1]))
                .collect(Collectors.toList());

        return new AnalyticsResponse(shortCode, entity.getClickCount(), byDay, byReferrer);
    }

    private Map<String, Object> row(Object key, String keyLabel, Object count) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(keyLabel, key);
        map.put("count", count);
        return map;
    }
}
