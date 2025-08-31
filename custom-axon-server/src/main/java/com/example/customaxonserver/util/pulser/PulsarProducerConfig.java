package com.example.customaxonserver.util.pulser;


import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PulsarProducerConfig {

  @Bean
  public PulsarClient pulsarClient() throws PulsarClientException {
    return PulsarClient.builder()
        .serviceUrl("pulsar://127.0.0.1:6650")
        .enableTls(false)
        .build();
  }

}

