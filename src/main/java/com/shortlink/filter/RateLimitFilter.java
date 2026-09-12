package com.shortlink.filter;

import com.shortlink.exception.RateLimitExceededException;
import com.shortlink.service.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Applies the fixed-window rate limiter to POST /api/urls only — reads and
 * redirects are left unlimited since abuse there is a different, less pressing
 * problem (and would normally be handled at a CDN/edge layer instead).
 */
@Component
public class RateLimitFilter extends HttpFilter {

    private final RateLimiterService rateLimiterService;

    public RateLimitFilter(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        boolean isShortenRequest = "POST".equalsIgnoreCase(request.getMethod())
                && "/api/urls".equals(request.getRequestURI());

        if (isShortenRequest) {
            String clientKey = clientKey(request);
            try {
                rateLimiterService.checkLimit(clientKey);
            } catch (RateLimitExceededException ex) {
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(ex.getRetryAfterSeconds()));
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"error\":\"Too Many Requests\",\"message\":\"" + ex.getMessage() + "\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private String clientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
