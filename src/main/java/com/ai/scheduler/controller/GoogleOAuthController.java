package com.ai.scheduler.controller;

import com.ai.scheduler.service.GoogleOAuthTokenService;
import com.ai.scheduler.util.SecurityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@RestController
@RequestMapping("/auth/google/calendar")
public class GoogleOAuthController {

    private static final String GOOGLE_AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar";

    private final GoogleOAuthTokenService tokenService;

    @Value("${google.oauth.client-id}")
    private String clientId;

    @Value("${google.oauth.redirect-uri}")
    private String redirectUri;

    public GoogleOAuthController(GoogleOAuthTokenService tokenService) {
        this.tokenService = tokenService;
    }

    /**
     * Returns the Google OAuth2 authorization URL.
     * The frontend redirects the user there to grant calendar access.
     * Requires a valid JWT — so we know which user is connecting their calendar.
     */
    @GetMapping("/connect")
    public ResponseEntity<Map<String, String>> connect() {
        Long userId = SecurityUtils.currentUserId();

        String authUrl = UriComponentsBuilder
                .fromHttpUrl(GOOGLE_AUTH_ENDPOINT)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", CALENDAR_SCOPE)
                .queryParam("access_type", "offline")   // needed to receive a refresh token
                .queryParam("prompt", "consent")         // force consent so refresh token is always issued
                .queryParam("state", userId)             // carry userId through the redirect
                .build()
                .toUriString();

        return ResponseEntity.ok(Map.of("authUrl", authUrl));
    }

    /**
     * Google redirects here after the user grants/denies access.
     * This endpoint is intentionally permitted without JWT (see SecurityConfig)
     * because it is called by Google's servers, not the frontend.
     *
     * The userId is recovered from the `state` parameter we embedded in the auth URL.
     */
    @GetMapping("/callback")
    public ResponseEntity<Map<String, String>> callback(
            @RequestParam String code,
            @RequestParam String state,
            @RequestParam(required = false) String error) {

        if (error != null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Google OAuth denied: " + error));
        }

        Long userId;
        try {
            userId = Long.parseLong(state);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid state parameter"));
        }

        tokenService.exchangeAndSave(userId, code);

        return ResponseEntity.ok(Map.of("message", "Google Calendar connected successfully"));
    }
}
