package com.skillhub.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * High-performance, in-memory sliding window rate limiter.
 * Protects against certificate enumeration, answer spamming, double submissions, and coding runner exhaustion.
 */
@Service
public class RateLimitingService {

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    private static class TokenBucket {
        private final int maxTokens;
        private final long windowMillis;
        private final AtomicInteger tokens;
        private volatile long windowStartTime;

        public TokenBucket(int maxTokens, long windowMillis) {
            this.maxTokens = maxTokens;
            this.windowMillis = windowMillis;
            this.tokens = new AtomicInteger(maxTokens);
            this.windowStartTime = System.currentTimeMillis();
        }

        public synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            if (now - windowStartTime >= windowMillis) {
                tokens.set(maxTokens);
                windowStartTime = now;
            }
            if (tokens.get() > 0) {
                tokens.decrementAndGet();
                return true;
            }
            return false;
        }

        public synchronized long getRemainingWaitMillis() {
            long now = System.currentTimeMillis();
            long elapsed = now - windowStartTime;
            return Math.max(0, windowMillis - elapsed);
        }
    }

    /**
     * Checks if a request is allowed under the rate limit.
     * @param key unique bucket key (e.g. "cert-verify:127.0.0.1" or "code-submit:user-42")
     * @param maxRequests maximum allowed requests within the time window
     * @param windowSeconds time window in seconds
     * @return true if permitted, false if rate limited
     */
    public boolean allowRequest(String key, int maxRequests, int windowSeconds) {
        TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(maxRequests, windowSeconds * 1000L));
        return bucket.tryAcquire();
    }

    /**
     * Gets cooldown remaining wait time in seconds.
     */
    public long getRemainingCooldownSeconds(String key) {
        TokenBucket bucket = buckets.get(key);
        if (bucket == null) return 0;
        return (bucket.getRemainingWaitMillis() + 999) / 1000;
    }

    /**
     * Clears cached buckets to avoid memory buildup.
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        buckets.entrySet().removeIf(entry -> now - entry.getValue().windowStartTime > 3600000L);
    }
}
