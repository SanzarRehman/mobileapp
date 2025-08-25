package com.example.mainapplication.service;

import com.example.grpc.common.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.google.protobuf.Struct;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
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

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

/**
 * Service for publishing events to the custom axon server.
 * This service sends events from the main application to the custom server for persistence and distribution.
 */
@Service
public class CustomServerEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(CustomServerEventPublisher.class);
    
    private final RestTemplate restTemplate;
    private final String customServerUrl;
    private final String masterGrpcServerUrl;
    private final Integer masterGrpcServerPort;
    private final ObjectMapper objectMapper;  // Reuse across calls
    private final Map<Class<?>, Field> aggregateIdFieldCache = new ConcurrentHashMap<>();


    public CustomServerEventPublisher(
        @Qualifier("restTemplate") RestTemplate restTemplate,
        @Value("${app.custom-server.url:http://localhost:8081}") String customServerUrl, @org.springframework.beans.factory.annotation.Value("${app.grpc-server.url:localhost}")String masterGrpcServerUrl, @org.springframework.beans.factory.annotation.Value("${app.grpc-server.port:9060}")int masterGrpcServerPort, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.customServerUrl = customServerUrl;
      this.masterGrpcServerUrl = masterGrpcServerUrl;
      this.masterGrpcServerPort = masterGrpcServerPort;
      this.objectMapper = objectMapper;
    }

    /**
     * Publishes an event to the custom server for persistence and distribution.
     */
    @Retry(name = "eventForwarding")
    @CircuitBreaker(name = "eventForwarding", fallbackMethod = "publishEventFallback")
    public void publishEvent(EventMessage<?> eventMessage) {
        try {
            logger.debug("Publishing event {} to custom server", eventMessage.getPayloadType().getSimpleName());


            Object payloadObj = eventMessage.getPayload();
            ByteString payloadBytes;

            boolean useProtobufStruct = true; // or false, based on your configuration
            try {
                if (useProtobufStruct) {
                    Map<String, Object> map = objectMapper.convertValue(payloadObj, Map.class);
                    Struct.Builder structBuilder = Struct.newBuilder();
                    map.forEach((k, v) -> structBuilder.putFields(k, convertToValue(v)));
                    payloadBytes = structBuilder.build().toByteString();
                } else {
                    payloadBytes = ByteString.copyFromUtf8(objectMapper.writeValueAsString(payloadObj));
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize command payload", e);
            }
            // Create event payload for custom server
            Map<String, Object> eventPayload = createEventPayload(eventMessage);

            // Send to custom server

            ManagedChannel channel = ManagedChannelBuilder
                .forAddress(masterGrpcServerUrl, masterGrpcServerPort)
                .usePlaintext()
                .build();

            SubmitEventRequest request = SubmitEventRequest.newBuilder()
                .setEventId(eventMessage.getIdentifier())
                .setAggregateId(eventPayload.get("aggregateId") != null ? eventPayload.get("aggregateId").toString() : "")
                .setEventType(eventMessage.getPayloadType().getName())
                .setPayload(payloadBytes)
                .build();

            EventHandlingServiceGrpc.EventHandlingServiceBlockingStub stub =
                EventHandlingServiceGrpc.newBlockingStub(channel);

            SubmitEventResponse response = stub.submitEvent(request);

        } catch (RuntimeException e) {
          throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unused")
    private void publishEventFallback(EventMessage<?> eventMessage, Throwable t) {
        logger.warn("Falling back while publishing event {} due to: {}",
                eventMessage.getIdentifier(), t.toString());
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
            // Convert Axon's 0-based sequence numbers to 1-based for custom server
            payload.put("sequenceNumber", domainEvent.getSequenceNumber() + 1);
        }
        
        return payload;
    }

    /**
     * Convert Java object to Protobuf Value.
     * Can be extended recursively for nested maps or lists.
     */
    private com.google.protobuf.Value convertToValue(Object obj) {
        if (obj == null) return com.google.protobuf.Value.newBuilder().setNullValueValue(0).build();
        if (obj instanceof String) return com.google.protobuf.Value.newBuilder().setStringValue((String) obj).build();
        if (obj instanceof Number) return com.google.protobuf.Value.newBuilder().setNumberValue(((Number) obj).doubleValue()).build();
        if (obj instanceof Boolean) return com.google.protobuf.Value.newBuilder().setBoolValue((Boolean) obj).build();
        // Fallback: convert to string
        return com.google.protobuf.Value.newBuilder().setStringValue(obj.toString()).build();
    }
}