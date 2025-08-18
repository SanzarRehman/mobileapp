package com.example.mainapplication.service;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for publishing events to the custom axon server.
 * This service sends events from the main application to the custom server for persistence and distribution.
 */
@Service
public class CustomServerEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(CustomServerEventPublisher.class);
    
    private final RestTemplate restTemplate;
    private final String customServerUrl;

    public CustomServerEventPublisher(
            @Qualifier("restTemplate") RestTemplate restTemplate,
            @Value("${app.custom-server.url:http://localhost:8081}") String customServerUrl) {
        this.restTemplate = restTemplate;
        this.customServerUrl = customServerUrl;
    }

    /**
     * Publishes an event to the custom server for persistence and distribution.
     */
    public void publishEvent(EventMessage<?> eventMessage) {
        try {
            logger.debug("Publishing event {} to custom server", eventMessage.getPayloadType().getSimpleName());
            
            // Create event payload for custom server
            Map<String, Object> eventPayload = createEventPayload(eventMessage);
            
            // Send to custom server
            String url = customServerUrl + "/api/events/publish";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(eventPayload, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.debug("Successfully published event {} to custom server", 
                           eventMessage.getIdentifier());
            } else {
                logger.error("Failed to publish event to custom server: {}", response.getStatusCode());
            }
            
        } catch (Exception e) {
            logger.error("Error publishing event {} to custom server: {}", 
                        eventMessage.getIdentifier(), e.getMessage(), e);
            // Don't throw exception to avoid breaking the main flow
        }
    }

    /**
     * Creates event payload for the custom server.
     */
    private Map<String, Object> createEventPayload(EventMessage<?> eventMessage) {
        Map<String, Object> payload = new HashMap<>();
        
        payload.put("eventId", eventMessage.getIdentifier());
        payload.put("eventType", eventMessage.getPayloadType().getName());
        payload.put("payload", eventMessage.getPayload());
        payload.put("metadata", eventMessage.getMetaData());
        payload.put("timestamp", eventMessage.getTimestamp().toString());
        
        // Add aggregate information if it's a domain event
        if (eventMessage instanceof DomainEventMessage) {
            DomainEventMessage<?> domainEvent = (DomainEventMessage<?>) eventMessage;
            payload.put("aggregateId", domainEvent.getAggregateIdentifier());
            payload.put("aggregateType", domainEvent.getType());
            payload.put("sequenceNumber", domainEvent.getSequenceNumber());
        }
        
        return payload;
    }
}