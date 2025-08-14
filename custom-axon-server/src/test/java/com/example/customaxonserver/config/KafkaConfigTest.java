package com.example.customaxonserver.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for Kafka configuration.
 * Verifies that all Kafka beans are properly configured.
 */
@SpringBootTest
@ActiveProfiles("test")
class KafkaConfigTest {

    @Autowired
    private KafkaConfig kafkaConfig;

    @Autowired
    private KafkaAdmin kafkaAdmin;

    @Autowired
    private ProducerFactory<String, Object> producerFactory;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private NewTopic eventsTopic;

    @Autowired
    private NewTopic commandsTopic;

    @Autowired
    private NewTopic snapshotsTopic;

    @Test
    void shouldConfigureKafkaAdmin() {
        assertThat(kafkaAdmin).isNotNull();
        assertThat(kafkaAdmin.getConfigurationProperties()).isNotEmpty();
    }

    @Test
    void shouldConfigureProducerFactory() {
        assertThat(producerFactory).isNotNull();
        assertThat(producerFactory.getConfigurationProperties()).isNotEmpty();
    }

    @Test
    void shouldConfigureKafkaTemplate() {
        assertThat(kafkaTemplate).isNotNull();
        assertThat(kafkaTemplate.getProducerFactory()).isEqualTo(producerFactory);
    }

    @Test
    void shouldConfigureEventsTopicCorrectly() {
        assertThat(eventsTopic).isNotNull();
        assertThat(eventsTopic.name()).isEqualTo("test-axon-events");
        assertThat(eventsTopic.numPartitions()).isEqualTo(2);
        assertThat(eventsTopic.replicationFactor()).isEqualTo((short) 1);
        
        // Verify topic-specific configurations
        assertThat(eventsTopic.configs()).containsEntry("cleanup.policy", "compact");
        assertThat(eventsTopic.configs()).containsEntry("retention.ms", "604800000");
    }

    @Test
    void shouldConfigureCommandsTopicCorrectly() {
        assertThat(commandsTopic).isNotNull();
        assertThat(commandsTopic.name()).isEqualTo("test-axon-commands");
        assertThat(commandsTopic.numPartitions()).isEqualTo(2);
        assertThat(commandsTopic.replicationFactor()).isEqualTo((short) 1);
        
        // Verify topic-specific configurations
        assertThat(commandsTopic.configs()).containsEntry("retention.ms", "86400000");
    }

    @Test
    void shouldConfigureSnapshotsTopicCorrectly() {
        assertThat(snapshotsTopic).isNotNull();
        assertThat(snapshotsTopic.name()).isEqualTo("test-axon-snapshots");
        assertThat(snapshotsTopic.numPartitions()).isEqualTo(2);
        assertThat(snapshotsTopic.replicationFactor()).isEqualTo((short) 1);
        
        // Verify topic-specific configurations
        assertThat(snapshotsTopic.configs()).containsEntry("cleanup.policy", "compact");
        assertThat(snapshotsTopic.configs()).containsEntry("retention.ms", "2592000000");
    }

    @Test
    void shouldProvideTopicNames() {
        assertThat(kafkaConfig.getEventsTopicName()).isEqualTo("test-axon-events");
        assertThat(kafkaConfig.getCommandsTopicName()).isEqualTo("test-axon-commands");
        assertThat(kafkaConfig.getSnapshotsTopicName()).isEqualTo("test-axon-snapshots");
    }
}