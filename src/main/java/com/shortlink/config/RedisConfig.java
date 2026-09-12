package com.shortlink.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate redisTemplate(RedisConnectionFactory connectionFactory) {
        // Plain string key/value is all we need: short_code -> long_url for the cache,
        // and rate-limit counters. No custom serializers required.
        return new StringRedisTemplate(connectionFactory);
    }
}
