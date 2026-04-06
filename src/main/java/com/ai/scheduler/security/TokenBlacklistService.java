package com.ai.scheduler.security;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TokenBlacklistService {
    private final Map<String, Instant> revoked = new ConcurrentHashMap<>();

    public void revoke(String token, Instant expiresAt) {
        revoked.put(token, expiresAt);
    }

    public boolean isRevoked(String token) {
        Instant expiry = revoked.get(token);
        return expiry != null && expiry.isAfter(Instant.now());
    }

    @Scheduled(fixedDelay = 300000)
    public void cleanup() {
        Instant now = Instant.now();
        revoked.entrySet().removeIf(e -> e.getValue().isBefore(now));
    }
}
