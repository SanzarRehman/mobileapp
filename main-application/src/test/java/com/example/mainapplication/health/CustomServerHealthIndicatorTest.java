package com.example.mainapplication.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomServerHealthIndicatorTest {

    @Mock
    private RestTemplate restTemplate;

    private CustomServerHealthIndicator healthIndicator;
    private final String customServerUrl = "http://localhost:8081";

    @BeforeEach
    void setUp() {
        healthIndicator = new CustomServerHealthIndicator(restTemplate, customServerUrl);
    }

    @Test
    void health_WhenCustomServerIsHealthy_ShouldReturnUp() {
        // Given
        String healthUrl = customServerUrl + "/actuator/health";
        when(restTemplate.getForObject(healthUrl, String.class)).thenReturn("OK");

        // When
        Health health = healthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("customServer", "Available");
        assertThat(health.getDetails()).containsEntry("url", customServerUrl);
        assertThat(health.getDetails()).containsEntry("response", "OK");

        verify(restTemplate).getForObject(healthUrl, String.class);
    }

    @Test
    void health_WhenCustomServerIsUnavailable_ShouldReturnDown() {
        // Given
        String healthUrl = customServerUrl + "/actuator/health";
        RestClientException exception = new RestClientException("Connection refused");
        when(restTemplate.getForObject(healthUrl, String.class)).thenThrow(exception);

        // When
        Health health = healthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("customServer", "Unavailable");
        assertThat(health.getDetails()).containsEntry("url", customServerUrl);
        assertThat(health.getDetails()).containsEntry("error", "org.springframework.web.client.RestClientException: Connection refused");
    }

    @Test
    void health_WhenCustomServerReturnsError_ShouldReturnDown() {
        // Given
        String healthUrl = customServerUrl + "/actuator/health";
        RuntimeException exception = new RuntimeException("Internal server error");
        when(restTemplate.getForObject(healthUrl, String.class)).thenThrow(exception);

        // When
        Health health = healthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("customServer", "Unavailable");
        assertThat(health.getDetails()).containsEntry("url", customServerUrl);
        assertThat(health.getDetails()).containsEntry("error", "java.lang.RuntimeException: Internal server error");
    }
}