package com.example.customaxonserver.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisHealthIndicatorTest {

    @Mock
    private RedisConnectionFactory redisConnectionFactory;

    @Mock
    private RedisConnection redisConnection;

    private RedisHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new RedisHealthIndicator(redisConnectionFactory);
    }

    @Test
    void health_WhenRedisIsHealthy_ShouldReturnUp() {
        // Given
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");

        // When
        Health health = healthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("redis", "Available");
        assertThat(health.getDetails()).containsEntry("ping", "PONG");

        verify(redisConnection).ping();
        verify(redisConnection).close();
    }

    @Test
    void health_WhenRedisConnectionFails_ShouldReturnDown() {
        // Given
        RuntimeException exception = new RuntimeException("Redis connection failed");
        when(redisConnectionFactory.getConnection()).thenThrow(exception);

        // When
        Health health = healthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("redis", "Unavailable");
        assertThat(health.getDetails()).containsEntry("error", "java.lang.RuntimeException: Redis connection failed");
    }

    @Test
    void health_WhenPingFails_ShouldReturnDown() {
        // Given
        RuntimeException exception = new RuntimeException("Ping failed");
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenThrow(exception);

        // When
        Health health = healthIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("redis", "Unavailable");
        assertThat(health.getDetails()).containsEntry("error", "java.lang.RuntimeException: Ping failed");
    }
}