package com.shortlink.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public class AnalyticsResponse {
    private String shortCode;
    private long totalClicks;
    private List<Map<String, Object>> clicksByDay;      // [{ "date": "...", "count": N }, ...]
    private List<Map<String, Object>> clicksByReferrer;  // [{ "referrer": "...", "count": N }, ...]
}
