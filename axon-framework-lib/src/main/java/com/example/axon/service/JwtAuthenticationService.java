package com.example.axon.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service for handling JWT authentication with Keycloak for service-to-service communication
 */
@Service
public class JwtAuthenticationService {

    @Value("${keycloak.auth-server-url}")
    private String keycloakServerUrl;

    @Value("${keycloak.realm}")
    private String keycloakRealm;

    @Value("${keycloak.resource}")
    private String clientId;

    @Value("${keycloak.credentials.secret}")
    private String clientSecret;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, TokenInfo> tokenCache = new ConcurrentHashMap<>();
    private final ReentrantLock tokenLock = new ReentrantLock();

    public JwtAuthenticationService(@Qualifier("restTemplate") RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Get a valid JWT token for service-to-service communication
     */
    public String getServiceToken() {
        String cacheKey = "service_token";
        TokenInfo tokenInfo = tokenCache.get(cacheKey);

        if (tokenInfo != null && !isTokenExpired(tokenInfo)) {
            return tokenInfo.getAccessToken();
        }

        tokenLock.lock();
        try {
            // Double-check after acquiring lock
            tokenInfo = tokenCache.get(cacheKey);
            if (tokenInfo != null && !isTokenExpired(tokenInfo)) {
                return tokenInfo.getAccessToken();
            }

            // Request new token
            TokenResponse tokenResponse = requestToken();
            tokenInfo = new TokenInfo(
                tokenResponse.getAccessToken(),
                Instant.now().plusSeconds(tokenResponse.getExpiresIn() - 30) // 30 seconds buffer
            );
            tokenCache.put(cacheKey, tokenInfo);

            return tokenInfo.getAccessToken();
        } finally {
            tokenLock.unlock();
        }
    }

    private TokenResponse requestToken() {
        String tokenUrl = keycloakServerUrl + "/realms/" + keycloakRealm + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                tokenUrl, request, TokenResponse.class);
            
            if (response.getBody() == null) {
                throw new RuntimeException("Failed to obtain JWT token: empty response");
            }
            
            return response.getBody();
        } catch (Exception e) {
            throw new RuntimeException("Failed to obtain JWT token from Keycloak", e);
        }
    }

    private boolean isTokenExpired(TokenInfo tokenInfo) {
        return Instant.now().isAfter(tokenInfo.getExpiresAt());
    }

    /**
     * Clear the token cache (useful for testing or when tokens are invalidated)
     */
    public void clearTokenCache() {
        tokenCache.clear();
    }

    private static class TokenInfo {
        private final String accessToken;
        private final Instant expiresAt;

        public TokenInfo(String accessToken, Instant expiresAt) {
            this.accessToken = accessToken;
            this.expiresAt = expiresAt;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public Instant getExpiresAt() {
            return expiresAt;
        }
    }

    public static class TokenResponse {
        @JsonProperty("access_token")
        private String accessToken;

        @JsonProperty("expires_in")
        private Long expiresIn;

        @JsonProperty("token_type")
        private String tokenType;

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public Long getExpiresIn() {
            return expiresIn;
        }

        public void setExpiresIn(Long expiresIn) {
            this.expiresIn = expiresIn;
        }

        public String getTokenType() {
            return tokenType;
        }

        public void setTokenType(String tokenType) {
            this.tokenType = tokenType;
        }
    }
}