package com.example.mainapplication.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.axonframework.commandhandling.CommandMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for routing commands to the custom Axon server.
 */
@Service
public class CustomServerCommandService {

    private static final Logger logger = LoggerFactory.getLogger(CustomServerCommandService.class);
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String customServerUrl;

    public CustomServerCommandService(RestTemplate restTemplate, 
                                    ObjectMapper objectMapper,
                                    @Value("${app.custom-server.url:http://localhost:8081}") String customServerUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.customServerUrl = customServerUrl;
    }

    /**
     * Routes a command to the custom server.
     */
    public <T> T routeCommand(CommandMessage<?> commandMessage, Class<T> responseType) {
        try {
            String url = customServerUrl + "/api/commands";
            
            // Create request payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("commandType", commandMessage.getCommandName());
            payload.put("commandId", commandMessage.getIdentifier());
            payload.put("payload", commandMessage.getPayload());
            payload.put("metadata", commandMessage.getMetaData());

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            logger.debug("Routing command {} to custom server: {}", 
                    commandMessage.getCommandName(), url);

            ResponseEntity<T> response = restTemplate.exchange(
                    url, 
                    HttpMethod.POST, 
                    request, 
                    responseType
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.debug("Command {} routed successfully", commandMessage.getCommandName());
                return response.getBody();
            } else {
                throw new RuntimeException("Failed to route command: " + response.getStatusCode());
            }

        } catch (Exception e) {
            logger.error("Error routing command {} to custom server", 
                    commandMessage.getCommandName(), e);
            throw new RuntimeException("Failed to route command to custom server", e);
        }
    }

    /**
     * Check if custom server is available.
     */
    public boolean isCustomServerAvailable() {
        try {
            String healthUrl = customServerUrl + "/actuator/health";
            ResponseEntity<String> response = restTemplate.getForEntity(healthUrl, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            logger.warn("Custom server is not available: {}", e.getMessage());
            return false;
        }
    }
}