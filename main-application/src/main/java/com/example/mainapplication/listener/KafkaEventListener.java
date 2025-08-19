package com.example.mainapplication.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.messaging.MetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class KafkaEventListener {

    private static final Logger logger = LoggerFactory.getLogger(KafkaEventListener.class);

    private final EventBus eventBus;
    private final ObjectMapper objectMapper;

    @Autowired
    public KafkaEventListener(EventBus eventBus, ObjectMapper objectMapper) {
        this.eventBus = eventBus;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "axon-events", groupId = "axon-consumers")
    public void consume(ConsumerRecord<String, Object> record) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) record.value();

            String eventType = (String) message.get("eventType");
            @SuppressWarnings("unchecked")
            Map<String, Object> eventData = (Map<String, Object>) message.get("eventData");
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) message.getOrDefault("metadata", Map.of());

            MetaData meta = MetaData.from(metadata).and("fromKafka", true);

            Class<?> clazz = Class.forName(eventType);
            Object event = objectMapper.convertValue(eventData, clazz);

            GenericEventMessage<?> eventMessage = new GenericEventMessage<>(event, meta);
            eventBus.publish(eventMessage);

            logger.info("Published event type {} from Kafka topic {}", eventType, record.topic());
        } catch (Exception e) {
            logger.error("Failed to consume Kafka event: {}", record, e);
            // Rely on container error handler/backoff for retries
            throw new RuntimeException(e);
        }
    }
}