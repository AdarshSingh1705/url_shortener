package com.shortlink.controller;

import com.shortlink.dto.response.AnalyticsResponse;
import com.shortlink.service.AnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/api/urls/{shortCode}/analytics")
    public AnalyticsResponse getAnalytics(@PathVariable String shortCode) {
        return analyticsService.getAnalytics(shortCode);
    }
}
