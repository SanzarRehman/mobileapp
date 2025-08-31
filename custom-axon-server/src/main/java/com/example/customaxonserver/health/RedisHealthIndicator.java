package com.example.customaxonserver.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory redisConnectionFactory;

    public RedisHealthIndicator(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Override
    public Health health() {
        try {
            RedisConnection connection = redisConnectionFactory.getConnection();
            
            // Execute PING command to test Redis connectivity
            String pong = connection.ping();
            connection.close();
            
            return Health.up()
                    .withDetail("redis", "Available")
                    .withDetail("ping", pong)
                    .build();
                    
        } catch (Exception e) {
            return Health.down()
                    .withDetail("redis", "Unavailable")
                    .withDetail("error", e.getMessage())
                    .withException(e)
                    .build();
        }
    }
}