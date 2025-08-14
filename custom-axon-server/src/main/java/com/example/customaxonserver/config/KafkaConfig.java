package com.example.customaxonserver.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for event publishing and topic management.
 * Configures producers, topics, and serialization for the custom Axon server.
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${kafka.topics.events:axon-events}")
    private String eventsTopicName;

    @Value("${kafka.topics.commands:axon-commands}")
    private String commandsTopicName;

    @Value("${kafka.topics.snapshots:axon-snapshots}")
    private String snapshotsTopicName;

    @Value("${kafka.topics.partitions:3}")
    private int defaultPartitions;

    @Value("${kafka.topics.replication-factor:1}")
    private short replicationFactor;

    /**
     * Kafka admin configuration for topic management.
     */
    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    /**
     * Producer factory for event publishing with JSON serialization.
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // Producer optimization settings
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 1);
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Kafka template for sending messages.
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * Events topic configuration.
     * Partitioned by aggregate ID for proper event ordering per aggregate.
     */
    @Bean
    public NewTopic eventsTopic() {
        return TopicBuilder.name(eventsTopicName)
                .partitions(defaultPartitions)
                .replicas(replicationFactor)
                .config("cleanup.policy", "compact")
                .config("retention.ms", "604800000") // 7 days
                .build();
    }

    /**
     * Commands topic configuration.
     * Partitioned by aggregate ID for command routing.
     */
    @Bean
    public NewTopic commandsTopic() {
        return TopicBuilder.name(commandsTopicName)
                .partitions(defaultPartitions)
                .replicas(replicationFactor)
                .config("retention.ms", "86400000") // 1 day
                .build();
    }

    /**
     * Snapshots topic configuration.
     * Used for distributing snapshots across instances.
     */
    @Bean
    public NewTopic snapshotsTopic() {
        return TopicBuilder.name(snapshotsTopicName)
                .partitions(defaultPartitions)
                .replicas(replicationFactor)
                .config("cleanup.policy", "compact")
                .config("retention.ms", "2592000000") // 30 days
                .build();
    }

    // Getters for topic names (used by services)
    public String getEventsTopicName() {
        return eventsTopicName;
    }

    public String getCommandsTopicName() {
        return commandsTopicName;
    }

    public String getSnapshotsTopicName() {
        return snapshotsTopicName;
    }
}