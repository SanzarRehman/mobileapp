package com.example.customaxonserver.util.pulser;


import com.example.grpc.common.SubmitEventRequest;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PulsarProducerFactory {
  private final PulsarClient client;
  private final Map<String, Producer<byte[]>> producerMap = new HashMap<>();
  public PulsarProducerFactory(PulsarClient client) {
    this.client = client;
  }

  public void createProducers(List<String> topics) throws PulsarClientException, PulsarClientException {
    Map<String, Producer<String>> map = new HashMap<>();
    for (String topic : topics) {
      try {
        String simpleName = topic.substring(topic.lastIndexOf('.') + 1);
        if (!producerMap.containsKey(topic)) {
          Producer<byte[]> producer = client.newProducer(Schema.AUTO_PRODUCE_BYTES())
              .topic(simpleName)
              .producerName("producer-" + simpleName)
              .create();
          producerMap.put(topic, (Producer<byte[]>) producer);
        }
      } catch (PulsarClientException e) {
        System.err.println("Failed to create producer for topic: " + topic);
      }
    }
  }

  public void sendMessage(String topic, SubmitEventRequest event) throws PulsarClientException {
    Producer<byte[]> producer = producerMap.get(topic);
    if (producer != null) {
      try {
        byte[] data = SerializerDeserializerCache.serialize(event);
        producer.newMessage()
            .value(data)
            .property("className", event.getClass().getName())
            .send();
      } catch (Exception e) {
        throw new RuntimeException("Failed to serialize event for topic " + topic, e);
      }
    } else {
      throw new IllegalStateException("Producer not found for topic: " + topic);
    }
  }

  // Close all producers
  public void closeAll() {
    producerMap.values().forEach(producer -> {
      try {
        producer.close();
      } catch (PulsarClientException e) {
        e.printStackTrace();
      }
    });
    producerMap.clear();
  }
}