package com.ai.scheduler.service;

import com.ai.scheduler.entity.GoogleOAuthToken;
import com.ai.scheduler.entity.User;
import com.ai.scheduler.repository.GoogleOAuthTokenRepository;
import com.ai.scheduler.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
public class GoogleOAuthTokenService {

    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";

    private final GoogleOAuthTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${google.oauth.client-id}")
    private String clientId;

    @Value("${google.oauth.client-secret}")
    private String clientSecret;

    @Value("${google.oauth.redirect-uri}")
    private String redirectUri;

    public GoogleOAuthTokenService(GoogleOAuthTokenRepository tokenRepository,
                                   UserRepository userRepository,
                                   RestTemplate restTemplate,
                                   ObjectMapper objectMapper) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Exchanges an authorization code (received after the OAuth consent screen)
     * for access + refresh tokens and persists them for the given user.
     */
    @Transactional
    public GoogleOAuthToken exchangeAndSave(Long userId, String authorizationCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        Map<String, Object> tokenResponse = exchangeCodeForTokens(authorizationCode);

        GoogleOAuthToken token = tokenRepository.findByUserId(userId)
                .orElseGet(() -> {
                    GoogleOAuthToken t = new GoogleOAuthToken();
                    t.setUser(user);
                    return t;
                });

        applyTokenResponse(token, tokenResponse);
        return tokenRepository.save(token);
    }

    /**
     * Returns a valid access token for the given user, refreshing it automatically
     * if it has expired or is about to expire (within 60 seconds).
     */
    @Transactional
    public String getValidAccessToken(Long userId) {
        GoogleOAuthToken token = tokenRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "No Google OAuth token found for user: " + userId));

        if (isExpiredOrExpiringSoon(token)) {
            token = refresh(token);
        }

        return token.getAccessToken();
    }

    /**
     * Uses the stored refresh token to obtain a new access token from Google
     * and persists the updated token.
     */
    @Transactional
    public GoogleOAuthToken refresh(GoogleOAuthToken token) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "refresh_token");
        params.add("refresh_token", token.getRefreshToken());
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);

        Map<String, Object> response = postForm(params);

        token.setAccessToken((String) response.get("access_token"));

        // Google only returns a new refresh token if rotation is enabled
        if (response.containsKey("refresh_token")) {
            token.setRefreshToken((String) response.get("refresh_token"));
        }

        int expiresIn = ((Number) response.get("expires_in")).intValue();
        token.setAccessTokenExpiresAt(Instant.now().plusSeconds(expiresIn));

        return tokenRepository.save(token);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private Map<String, Object> exchangeCodeForTokens(String authorizationCode) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("code", authorizationCode);
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri", redirectUri);

        return postForm(params);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postForm(MultiValueMap<String, String> params) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        Map<String, Object> response;
        try {
            response = restTemplate.postForObject(TOKEN_ENDPOINT, request, Map.class);
        } catch (HttpClientErrorException e) {
            throw new RuntimeException(
                    "Google token endpoint returned " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
        }

        if (response == null) {
            throw new RuntimeException("Empty response from Google token endpoint");
        }
        if (response.containsKey("error")) {
            throw new RuntimeException("Google token error: " + response.get("error")
                    + " – " + response.get("error_description"));
        }
        return response;
    }

    private void applyTokenResponse(GoogleOAuthToken token, Map<String, Object> response) {
        token.setAccessToken((String) response.get("access_token"));

        // refresh_token is only present on the initial code exchange
        if (response.containsKey("refresh_token")) {
            token.setRefreshToken((String) response.get("refresh_token"));
        }

        int expiresIn = ((Number) response.get("expires_in")).intValue();
        token.setAccessTokenExpiresAt(Instant.now().plusSeconds(expiresIn));

        if (response.containsKey("scope")) {
            token.setScopes((String) response.get("scope"));
        }
    }

    private boolean isExpiredOrExpiringSoon(GoogleOAuthToken token) {
        return token.getAccessTokenExpiresAt().isBefore(Instant.now().plusSeconds(60));
    }
}
