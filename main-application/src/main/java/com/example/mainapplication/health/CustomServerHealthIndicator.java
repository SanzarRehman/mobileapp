package com.example.mainapplication.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class CustomServerHealthIndicator implements HealthIndicator {

    private final RestTemplate restTemplate;
    private final String customServerUrl;

    public CustomServerHealthIndicator(RestTemplate restTemplate,
                                     @Value("${custom.axon.server.url:http://localhost:8081}") String customServerUrl) {
        this.restTemplate = restTemplate;
        this.customServerUrl = customServerUrl;
    }

    @Override
    public Health health() {
        try {
            String healthUrl = customServerUrl + "/actuator/health";
            String response = restTemplate.getForObject(healthUrl, String.class);
            
            return Health.up()
                    .withDetail("customServer", "Available")
                    .withDetail("url", customServerUrl)
                    .withDetail("response", "OK")
                    .build();
                    
        } catch (Exception e) {
            return Health.down()
                    .withDetail("customServer", "Unavailable")
                    .withDetail("url", customServerUrl)
                    .withDetail("error", e.getMessage())
                    .withException(e)
                    .build();
        }
    }
}