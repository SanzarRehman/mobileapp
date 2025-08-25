package com.example.mainapplication.service;


import org.apache.pulsar.client.api.*;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PulsarDynamicConsumerService {

  private final PulsarClient client;
  private final Map<String, Consumer<byte[]>> consumers = new ConcurrentHashMap<>();

  public PulsarDynamicConsumerService(PulsarClient client) {
    this.client = client;
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
        consumer.acknowledge(msg);
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

