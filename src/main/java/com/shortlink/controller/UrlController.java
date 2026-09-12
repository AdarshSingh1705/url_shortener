package com.shortlink.controller;

import com.shortlink.dto.request.ShortenRequest;
import com.shortlink.dto.response.ShortenResponse;
import com.shortlink.service.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
public class UrlController {

    private final UrlService urlService;

    public UrlController(UrlService urlService) {
        this.urlService = urlService;
    }

    @PostMapping("/api/urls")
    public ResponseEntity<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest request) {
        ShortenResponse response = urlService.shorten(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode, HttpServletRequest request) {
        String longUrl = urlService.resolveAndRecordClick(
                shortCode,
                request.getHeader(HttpHeaders.REFERER),
                clientIp(request));

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(longUrl))
                .build();
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
