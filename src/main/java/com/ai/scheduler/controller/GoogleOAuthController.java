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

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/auth/google/calendar")
public class GoogleOAuthController {

    private static final String GOOGLE_AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar";

    /** Separator used to pack userId + appRedirectUri into the OAuth state param. */
    private static final String STATE_SEP = "|";

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
     *
     * <p>Web clients: call with no extra params — the callback returns JSON.
     *
     * <p>Mobile clients (flutter_web_auth_2): pass {@code appRedirectUri}
     * (e.g. {@code com.yourapp://oauth}). After the token exchange the backend
     * redirects the in-app browser to that URI so the package can intercept it
     * and close the browser automatically.
     *
     * <p>Only custom URI schemes (non-http/https) are accepted as
     * {@code appRedirectUri} to prevent open-redirect attacks.
     */
    @GetMapping("/connect")
    public ResponseEntity<Map<String, String>> connect(
            @RequestParam(required = false) String appRedirectUri) {

        Long userId = SecurityUtils.currentUserId();

        // Build the state param: "userId" or "userId|appRedirectUri"
        String state = buildState(userId, appRedirectUri);

        String authUrl = UriComponentsBuilder
                .fromHttpUrl(GOOGLE_AUTH_ENDPOINT)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", CALENDAR_SCOPE)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", state)
                .build()
                .toUriString();

        return ResponseEntity.ok(Map.of("authUrl", authUrl));
    }

    /**
     * Google redirects here after the user grants/denies access.
     *
     * <ul>
     *   <li>Web flow: returns {@code 200 JSON}.
     *   <li>Mobile flow: returns {@code 302} redirect to the app's custom URI scheme
     *       so {@code flutter_web_auth_2} can close the in-app browser.
     * </ul>
     */
    @GetMapping("/callback")
    public ResponseEntity<?> callback(
            @RequestParam String code,
            @RequestParam String state,
            @RequestParam(required = false) String error) {

        if (error != null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Google OAuth denied: " + error));
        }

        Long userId;
        String appRedirectUri = null;
        try {
            String[] parts = state.split("\\" + STATE_SEP, 2);
            userId = Long.parseLong(parts[0]);
            if (parts.length == 2 && !parts[1].isBlank()) {
                appRedirectUri = parts[1];
            }
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid state parameter"));
        }

        tokenService.exchangeAndSave(userId, code);

        // Mobile flow: redirect the in-app browser to the app's custom scheme
        if (appRedirectUri != null) {
            URI destination = URI.create(appRedirectUri + "?connected=true");
            return ResponseEntity.status(302).location(destination).build();
        }

        // Web flow: plain JSON response
        return ResponseEntity.ok(Map.of("message", "Google Calendar connected successfully"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String buildState(Long userId, String appRedirectUri) {
        if (appRedirectUri == null || appRedirectUri.isBlank()) {
            return String.valueOf(userId);
        }

        // Reject http/https to prevent open-redirect to arbitrary web pages
        String lower = appRedirectUri.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            throw new IllegalArgumentException(
                    "appRedirectUri must be a custom URI scheme (e.g. com.yourapp://oauth), not http/https");
        }

        return userId + STATE_SEP + appRedirectUri;
    }
}
