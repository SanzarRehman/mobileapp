package com.example.mainapplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.MetaData;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class KafkaEventConsumer {

  private final ObjectMapper objectMapper;
  private final EventBus eventBus;

  public KafkaEventConsumer(ObjectMapper objectMapper, EventBus eventBus) {
    this.objectMapper = objectMapper;
    this.eventBus = eventBus;
  }

  @KafkaListener(topics = "axon-events", groupId = "axon-consumers")
  public void consume(ConsumerRecord<String, Object> record) {
    try {
      Map<String, Object> message = (Map<String, Object>) record.value();

      // Extract fields
      String eventType = (String) message.get("eventType");
      Map<String, Object> eventData = (Map<String, Object>) message.get("eventData");
      Map<String, Object> metadata = (Map<String, Object>) message.get("metadata");

      MetaData meta = MetaData.from(metadata).and("fromKafka", true);

      // Convert eventData into actual event
      Class<?> clazz = Class.forName(eventType.toString());
      Object event = objectMapper.convertValue(eventData, clazz);

      // Wrap & publish
      GenericEventMessage<?> eventMessage =
          new GenericEventMessage<>(event, MetaData.from(metadata).and("fromKafka", true));

      eventBus.publish(eventMessage);

      System.out.println("✅ Published " + eventType);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}

