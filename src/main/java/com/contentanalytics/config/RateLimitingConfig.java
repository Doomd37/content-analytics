package com.contentanalytics.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@Component
public class RateLimitingConfig {

    private final Map<String, Bucket> cacheBuckets = new ConcurrentHashMap<>();

    // Get rate limit bucket for an IP address 5 requests per minute per IP

    public Bucket getLoginBucket(String clientIp) {
        return cacheBuckets.computeIfAbsent(clientIp, ip -> createLoginBucket());
    }

    /**
     * Get rate limit bucket for registration
     * 2 requests per hour per IP
     */
    public Bucket getRegisterBucket(String clientIp) {
        return cacheBuckets.computeIfAbsent("register:" + clientIp, ip -> createRegisterBucket());
    }

    private Bucket createLoginBucket() {
        Bandwidth limit = Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1)));
        return Bucket4j.builder()
                .addLimit(limit)
                .build();
    }

    private Bucket createRegisterBucket() {
        Bandwidth limit = Bandwidth.classic(2, Refill.intervally(2, Duration.ofHours(1)));
        return Bucket4j.builder()
                .addLimit(limit)
                .build();
    }

}
