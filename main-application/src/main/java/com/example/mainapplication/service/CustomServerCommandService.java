package com.example.mainapplication.service;

import com.example.mainapplication.messaging.DeadLetterQueueHandler;
import com.example.mainapplication.resilience.CircuitBreakerService;
import com.example.mainapplication.resilience.RetryService;
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
    private final CircuitBreakerService circuitBreakerService;
    private final RetryService retryService;
    private final DeadLetterQueueHandler deadLetterQueueHandler;

    public CustomServerCommandService(RestTemplate restTemplate, 
                                    ObjectMapper objectMapper,
                                    @Value("${app.custom-server.url:http://localhost:8081}") String customServerUrl,
                                    CircuitBreakerService circuitBreakerService,
                                    RetryService retryService,
                                    DeadLetterQueueHandler deadLetterQueueHandler) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.customServerUrl = customServerUrl;
        this.circuitBreakerService = circuitBreakerService;
        this.retryService = retryService;
        this.deadLetterQueueHandler = deadLetterQueueHandler;
    }

    /**
     * Routes a command to the custom server with resilience patterns.
     */
    public <T> T routeCommand(CommandMessage<?> commandMessage, Class<T> responseType) {
        String operationName = "routeCommand-" + commandMessage.getCommandName();
        
        return circuitBreakerService.executeWithCircuitBreaker("custom-server", () -> 
            retryService.executeWithRetry(operationName, () -> {
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
                    
                    // Send to dead letter queue if all retries fail
                    if (isNonRetryableError(e)) {
                        deadLetterQueueHandler.sendToDeadLetterQueue("commands", commandMessage, e);
                    }
                    
                    throw new RuntimeException("Failed to route command to custom server", e);
                }
            })
        );
    }

    /**
     * Check if custom server is available with circuit breaker.
     */
    public boolean isCustomServerAvailable() {
        try {
            return circuitBreakerService.executeWithCircuitBreaker("custom-server-health", () -> {
                String healthUrl = customServerUrl + "/actuator/health";
                ResponseEntity<String> response = restTemplate.getForEntity(healthUrl, String.class);
                return response.getStatusCode().is2xxSuccessful();
            });
        } catch (Exception e) {
            logger.warn("Custom server is not available: {}", e.getMessage());
            return false;
        }
    }

    private boolean isNonRetryableError(Exception e) {
        // Determine if the error should not be retried (e.g., validation errors)
        return e instanceof IllegalArgumentException ||
               (e.getMessage() != null && e.getMessage().contains("400"));
    }
}