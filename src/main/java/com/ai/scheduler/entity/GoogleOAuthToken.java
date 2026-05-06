package com.ai.scheduler.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "google_oauth_tokens")
public class GoogleOAuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** Short-lived access token used to call Google APIs. */
    @Column(name = "access_token", nullable = false, length = 2048)
    private String accessToken;

    /** Long-lived refresh token used to obtain a new access token. */
    @Column(name = "refresh_token", nullable = false, length = 512)
    private String refreshToken;

    /** Absolute point in time when the access token expires. */
    @Column(name = "access_token_expires_at", nullable = false)
    private Instant accessTokenExpiresAt;

    /** Scopes that were granted, e.g. "https://www.googleapis.com/auth/calendar". */
    @Column(name = "scopes", length = 1024)
    private String scopes;
}
