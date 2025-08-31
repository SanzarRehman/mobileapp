package com.example.mainapplication.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests for JWT authentication service
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private JwtAuthenticationService jwtAuthenticationService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        jwtAuthenticationService = new JwtAuthenticationService(restTemplate, objectMapper);
        
        // Set test configuration values
        ReflectionTestUtils.setField(jwtAuthenticationService, "keycloakServerUrl", "http://localhost:8180/auth");
        ReflectionTestUtils.setField(jwtAuthenticationService, "keycloakRealm", "axon-realm-test");
        ReflectionTestUtils.setField(jwtAuthenticationService, "clientId", "test-client");
        ReflectionTestUtils.setField(jwtAuthenticationService, "clientSecret", "test-secret");
    }

    @Test
    void shouldGetServiceTokenSuccessfully() {
        // Given
        JwtAuthenticationService.TokenResponse tokenResponse = new JwtAuthenticationService.TokenResponse();
        tokenResponse.setAccessToken("test-access-token");
        tokenResponse.setExpiresIn(3600L);
        tokenResponse.setTokenType("Bearer");

        when(restTemplate.postForEntity(anyString(), any(), any(Class.class)))
                .thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        // When
        String token = jwtAuthenticationService.getServiceToken();

        // Then
        assertNotNull(token);
        assertEquals("test-access-token", token);
    }

    @Test
    void shouldCacheTokenAndReuseIt() {
        // Given
        JwtAuthenticationService.TokenResponse tokenResponse = new JwtAuthenticationService.TokenResponse();
        tokenResponse.setAccessToken("cached-token");
        tokenResponse.setExpiresIn(3600L);
        tokenResponse.setTokenType("Bearer");

        when(restTemplate.postForEntity(anyString(), any(), any(Class.class)))
                .thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        // When
        String token1 = jwtAuthenticationService.getServiceToken();
        String token2 = jwtAuthenticationService.getServiceToken();

        // Then
        assertEquals(token1, token2);
    }

    @Test
    void shouldClearTokenCache() {
        // Given
        JwtAuthenticationService.TokenResponse tokenResponse = new JwtAuthenticationService.TokenResponse();
        tokenResponse.setAccessToken("test-token");
        tokenResponse.setExpiresIn(3600L);
        tokenResponse.setTokenType("Bearer");

        when(restTemplate.postForEntity(anyString(), any(), any(Class.class)))
                .thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        // When
        jwtAuthenticationService.getServiceToken();
        jwtAuthenticationService.clearTokenCache();

        // Then - should not throw exception
        assertDoesNotThrow(() -> jwtAuthenticationService.clearTokenCache());
    }

    @Test
    void shouldHandleTokenRequestFailure() {
        // Given
        when(restTemplate.postForEntity(anyString(), any(), any(Class.class)))
                .thenThrow(new RuntimeException("Connection failed"));

        // When & Then
        assertThrows(RuntimeException.class, () -> jwtAuthenticationService.getServiceToken());
    }

    @Test
    void shouldHandleEmptyTokenResponse() {
        // Given
        when(restTemplate.postForEntity(anyString(), any(), any(Class.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        // When & Then
        assertThrows(RuntimeException.class, () -> jwtAuthenticationService.getServiceToken());
    }
}