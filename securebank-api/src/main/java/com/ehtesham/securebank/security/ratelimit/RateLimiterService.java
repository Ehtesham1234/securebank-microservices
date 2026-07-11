package com.ehtesham.securebank.security.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

@Service
public class RateLimiterService {

    // one bucket PER unique key (IP address, or IP+email combo)
    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean tryConsume(String key, int capacity, Duration refillPeriod) {

        Bucket bucket = buckets.computeIfAbsent(
                key,
                k -> createNewBucket(capacity, refillPeriod));

        return bucket.tryConsume(1);
    }

    private Bucket createNewBucket(int capacity, Duration refillPeriod) {

        Bandwidth limit = Bandwidth.classic(
                capacity,
                io.github.bucket4j.Refill.intervally(capacity, refillPeriod));

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}