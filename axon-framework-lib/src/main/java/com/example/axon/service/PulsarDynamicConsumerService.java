package com.example.axon.service;


import com.example.axon.util.EventDeserializer;
import com.example.grpc.common.SubmitEventRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.pulsar.client.api.*;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.MetaData;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PulsarDynamicConsumerService {

  private final PulsarClient client;
  private final Map<String, Consumer<byte[]>> consumers = new ConcurrentHashMap<>();
  private final EventDeserializer eventDeserializer;
  private final EventBus eventBus;
  private final ObjectMapper objectMapper = new ObjectMapper();
  public PulsarDynamicConsumerService(PulsarClient client, EventDeserializer eventDeserializer, EventBus eventBus) {
    this.client = client;
    this.eventDeserializer = eventDeserializer;
    this.eventBus = eventBus;
  }

  public void subscribeToTopic(String topic, String subscriptionName) throws PulsarClientException {
    if (consumers.containsKey(topic)) {
      System.out.println("Already subscribed to: " + topic);
      return;
    }

    MessageListener<byte[]> listener = (consumer, msg) -> {
      try {
        System.out.printf("📩 Message from [%s]: %s%n",
            msg.getTopicName(),
            new String(msg.getData()));

        SubmitEventRequest request = SubmitEventRequest.parseFrom(msg.getData());
        String eventType = request.getEventType();

        String className = request.getEventType();
        Map<String, Object> payloadMap = eventDeserializer.reconstructPayload(request, true); // or true for Struct

        // Dynamically build command object
        Class<?> commandClass = Class.forName(className);

        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Object event = objectMapper.convertValue(payloadMap, commandClass);
        Map<String, String> metadata = request.getMetadata();
        MetaData meta = MetaData.from(metadata).and("fromKafka", true);
      GenericEventMessage<?> eventMessage =
          new GenericEventMessage<>(event, MetaData.from(meta).and("fromKafka", true));
       eventBus.publish(eventMessage);
      } catch (Exception e) {
        consumer.negativeAcknowledge(msg);
      }
    };

    Consumer<byte[]> consumer = client.newConsumer()
        .topic(topic)
        .subscriptionName(subscriptionName)
        .messageListener(listener)
        .subscribe();

    consumers.put(topic, consumer);
    System.out.println("✅ Subscribed dynamically to topic: " + topic);
  }

  public void unsubscribeFromTopic(String topic) throws PulsarClientException {
    Consumer<byte[]> consumer = consumers.remove(topic);
    if (consumer != null) {
      consumer.close();
      System.out.println("❌ Unsubscribed from topic: " + topic);
    }
  }
}
