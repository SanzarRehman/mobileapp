package com.example.axon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for fetching events from the custom Axon server.
 */
@Service
public class CustomServerEventFetcher {

    private static final Logger logger = LoggerFactory.getLogger(CustomServerEventFetcher.class);

    private final RestTemplate restTemplate;
    private final String customServerUrl;
    private final ObjectMapper objectMapper;

    public CustomServerEventFetcher(
            @Qualifier("restTemplate") RestTemplate restTemplate,
            @Value("${app.custom-server.url:http://localhost:8081}") String customServerUrl,
            ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.customServerUrl = customServerUrl;
        this.objectMapper = objectMapper;
    }

    /**
     * Fetches all events for an aggregate from the custom server.
     */
    @SuppressWarnings("unchecked")
    public List<EventData> fetchEventsForAggregate(String aggregateId) {
        try {
            logger.debug("Fetching events for aggregate {} from custom server", aggregateId);

            String url = customServerUrl + "/api/aggregates/" + aggregateId + "/events";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                List<Map<String, Object>> eventEntities = (List<Map<String, Object>>) responseBody.get("events");

                List<EventData> events = new ArrayList<>();

                for (Map<String, Object> eventEntity : eventEntities) {
                    String eventType = (String) eventEntity.get("eventType");
                    Map<String, Object> eventDataMap = (Map<String, Object>) eventEntity.get("eventData");
                    Number sequenceNumber = (Number) eventEntity.get("sequenceNumber");
                    String aggregateType = (String) eventEntity.get("aggregateType");

                    // Convert to actual event objects
                    Object event = convertToEventObject(eventType, eventDataMap);
                    if (event != null) {
                        events.add(new EventData(
                            event,
                            sequenceNumber.longValue(),
                            eventType,
                            aggregateType != null ? aggregateType : "UserAggregate"
                        ));
                    }
                }

                logger.debug("Successfully fetched {} events for aggregate {}", events.size(), aggregateId);
                return events;
            }

        } catch (Exception e) {
            logger.error("Failed to fetch events for aggregate {} from custom server: {}",
                        aggregateId, e.getMessage(), e);
        }

        return new ArrayList<>();
    }

    /**
     * Gets the current sequence number for an aggregate from the custom server.
     */
    @SuppressWarnings("unchecked")
    public Long getCurrentSequenceNumber(String aggregateId) {
        try {
            String url = customServerUrl + "/api/aggregates/" + aggregateId + "/sequence";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Number currentSequence = (Number) responseBody.get("currentSequence");
                return currentSequence != null ? currentSequence.longValue() : 0L;
            }

        } catch (Exception e) {
            logger.error("Failed to get sequence for aggregate {} from custom server: {}",
                        aggregateId, e.getMessage(), e);
        }

        return 0L;
    }

    private Object convertToEventObject(String eventType, Map<String, Object> eventData) {
        try {
            switch (eventType) {
                case "com.example.mainapplication.event.UserCreatedEvent":
//                    return new UserCreatedEvent(
//                        (String) eventData.get("userId"),
//                        (String) eventData.get("username"),
//                        (String) eventData.get("email"),
//                        (String) eventData.get("fullName"),
//                        parseTimestamp(eventData.get("createdAt"))
//                    );
                case "com.example.mainapplication.event.UserUpdatedEvent":
//                    return new UserUpdatedEvent(
//                        (String) eventData.get("userId"),
//                        (String) eventData.get("username"),
//                        (String) eventData.get("email"),
//                        (String) eventData.get("fullName"),
//                        parseTimestamp(eventData.get("updatedAt"))
//                    );
                default:
                    logger.warn("Unknown event type: {}", eventType);
                    return null;
            }
        } catch (Exception e) {
            logger.error("Failed to convert event data for type {}: {}", eventType, e.getMessage(), e);
            return null;
        }
    }

    private Instant parseTimestamp(Object timestampValue) {
        if (timestampValue == null) {
            return Instant.now();
        }

        if (timestampValue instanceof String) {
            return Instant.parse((String) timestampValue);
        }

        if (timestampValue instanceof Number) {
            return Instant.ofEpochMilli(((Number) timestampValue).longValue());
        }

        // If it's already an Instant (shouldn't happen from JSON but just in case)
        if (timestampValue instanceof Instant) {
            return (Instant) timestampValue;
        }

        // Fallback - try to convert to string and parse
        return Instant.parse(timestampValue.toString());
    }

    /**
     * Data class to hold event information from the custom server.
     */
    public static class EventData {
        private final Object event;
        private final long sequenceNumber;
        private final String eventType;
        private final String aggregateType;

        public EventData(Object event, long sequenceNumber, String eventType, String aggregateType) {
            this.event = event;
            this.sequenceNumber = sequenceNumber;
            this.eventType = eventType;
            this.aggregateType = aggregateType;
        }

        public Object getEvent() {
            return event;
        }

        public long getSequenceNumber() {
            return sequenceNumber;
        }

        public String getEventType() {
            return eventType;
        }

        public String getAggregateType() {
            return aggregateType;
        }
    }
}
