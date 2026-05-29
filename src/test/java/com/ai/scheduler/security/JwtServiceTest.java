package com.ai.scheduler.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    // 32-byte key base64-encoded — same length/format as the test app.properties secret
    private static final String SECRET = "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXphYmNkZWY=";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 60); // 60-minute expiry
    }

    private UserPrincipal principal(Long id, String email) {
        return new UserPrincipal(id, email, "hashed", true);
    }

    // -------------------------------------------------------------------------
    // generateToken / extractUsername / extractUserId
    // -------------------------------------------------------------------------

    @Test
    void generateToken_extractUsername_returnsEmail() {
        UserPrincipal p = principal(1L, "alice@example.com");

        String token = jwtService.generateToken(p);
        String username = jwtService.extractUsername(token);

        assertThat(username).isEqualTo("alice@example.com");
    }

    @Test
    void generateToken_extractUserId_returnsCorrectId() {
        UserPrincipal p = principal(42L, "alice@example.com");

        String token = jwtService.generateToken(p);
        Long userId = jwtService.extractUserId(token);

        assertThat(userId).isEqualTo(42L);
    }

    @Test
    void extractExpiration_returnsDateInFuture() {
        String token = jwtService.generateToken(principal(1L, "alice@example.com"));

        Date expiry = jwtService.extractExpiration(token);

        assertThat(expiry).isAfter(new Date());
    }

    // -------------------------------------------------------------------------
    // isValid
    // -------------------------------------------------------------------------

    @Test
    void isValid_correctPrincipal_returnsTrue() {
        UserPrincipal p = principal(1L, "alice@example.com");
        String token = jwtService.generateToken(p);

        assertThat(jwtService.isValid(token, p)).isTrue();
    }

    @Test
    void isValid_differentEmail_returnsFalse() {
        UserPrincipal issuedFor = principal(1L, "alice@example.com");
        UserPrincipal other = principal(2L, "bob@example.com");
        String token = jwtService.generateToken(issuedFor);

        assertThat(jwtService.isValid(token, other)).isFalse();
    }

    @Test
    void isValid_expiredToken_throwsExpiredJwtException() {
        // isValid() calls parseClaims() which throws ExpiredJwtException for expired tokens
        JwtService shortLived = new JwtService(SECRET, -1);
        UserPrincipal p = principal(1L, "alice@example.com");
        String token = shortLived.generateToken(p);

        assertThatThrownBy(() -> jwtService.isValid(token, p))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }

    // -------------------------------------------------------------------------
    // tampered token
    // -------------------------------------------------------------------------

    @Test
    void parseClaims_tamperedToken_throwsException() {
        String token = jwtService.generateToken(principal(1L, "alice@example.com"));
        // flip a character in the signature part
        String tampered = token.substring(0, token.length() - 1) + (token.charAt(token.length() - 1) == 'A' ? 'B' : 'A');

        assertThatThrownBy(() -> jwtService.extractUsername(tampered));
    }

    // -------------------------------------------------------------------------
    // multiple users get distinct tokens
    // -------------------------------------------------------------------------

    @Test
    void generateToken_differentPrincipals_produceDistinctTokens() {
        UserPrincipal p1 = principal(1L, "alice@example.com");
        UserPrincipal p2 = principal(2L, "bob@example.com");

        String t1 = jwtService.generateToken(p1);
        String t2 = jwtService.generateToken(p2);

        assertThat(t1).isNotEqualTo(t2);
        assertThat(jwtService.extractUserId(t1)).isEqualTo(1L);
        assertThat(jwtService.extractUserId(t2)).isEqualTo(2L);
    }
}
