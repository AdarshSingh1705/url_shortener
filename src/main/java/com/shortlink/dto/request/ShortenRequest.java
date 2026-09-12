package com.shortlink.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ShortenRequest {

    @NotBlank(message = "longUrl is required")
    @Pattern(regexp = "^https?://.+", message = "longUrl must start with http:// or https://")
    private String longUrl;

    // Optional: number of days until the link expires. Null/omitted = never expires.
    @Positive
    @Max(3650)
    private Integer expiresInDays;
}
