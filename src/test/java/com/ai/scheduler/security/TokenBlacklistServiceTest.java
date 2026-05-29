package com.ai.scheduler.security;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBlacklistServiceTest {

    private TokenBlacklistService service;

    @BeforeEach
    void setUp() {
        service = new TokenBlacklistService();
    }

    @Test
    void freshToken_isNotRevoked() {
        assertThat(service.isRevoked("unknown-token")).isFalse();
    }

    @Test
    void revokedToken_withFutureExpiry_isRevoked() {
        service.revoke("tok1", Instant.now().plusSeconds(3600));

        assertThat(service.isRevoked("tok1")).isTrue();
    }

    @Test
    void revokedToken_withPastExpiry_isNotConsideredRevoked() {
        // Expiry in the past — token is already naturally expired, not "active-revoked"
        service.revoke("tok2", Instant.now().minusSeconds(1));

        assertThat(service.isRevoked("tok2")).isFalse();
    }

    @Test
    void differentTokens_areTrackedIndependently() {
        service.revoke("tok-a", Instant.now().plusSeconds(60));
        service.revoke("tok-b", Instant.now().minusSeconds(1)); // past

        assertThat(service.isRevoked("tok-a")).isTrue();
        assertThat(service.isRevoked("tok-b")).isFalse();
        assertThat(service.isRevoked("tok-c")).isFalse();
    }

    @Test
    void cleanup_removesExpiredEntries() {
        service.revoke("expired-tok", Instant.now().minusSeconds(5));
        service.revoke("valid-tok", Instant.now().plusSeconds(3600));

        service.cleanup();

        // expired entry should be cleaned up
        assertThat(service.isRevoked("expired-tok")).isFalse();
        // valid entry should still be present
        assertThat(service.isRevoked("valid-tok")).isTrue();
    }

    @Test
    void revokeCalledTwice_lastWriteWins() {
        // Re-revoking with a past expiry should effectively "un-revoke" from the isRevoked perspective
        service.revoke("tok", Instant.now().plusSeconds(3600));
        service.revoke("tok", Instant.now().minusSeconds(1));

        assertThat(service.isRevoked("tok")).isFalse();
    }
}
