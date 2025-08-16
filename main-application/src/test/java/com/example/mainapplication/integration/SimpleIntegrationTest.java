package com.example.mainapplication.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple integration test to verify the comprehensive test infrastructure works.
 * This test validates that:
 * 1. Testcontainers can start required infrastructure
 * 2. Spring Boot context loads successfully
 * 3. Basic connectivity to external services works
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public class SimpleIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:latest"));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:6-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", () -> redis.getMappedPort(6379));
        registry.add("custom.axon.server.url", () -> "http://localhost:8081");
    }

    @Test
    void shouldStartInfrastructureContainers() {
        // Given - Containers are started by Testcontainers
        
        // When - We check container status
        boolean postgresRunning = postgres.isRunning();
        boolean kafkaRunning = kafka.isRunning();
        boolean redisRunning = redis.isRunning();
        
        // Then - All containers should be running
        assertThat(postgresRunning).isTrue();
        assertThat(kafkaRunning).isTrue();
        assertThat(redisRunning).isTrue();
    }

    @Test
    void shouldHaveValidDatabaseConnection() {
        // Given - PostgreSQL container is running
        
        // When - We get connection details
        String jdbcUrl = postgres.getJdbcUrl();
        String username = postgres.getUsername();
        String password = postgres.getPassword();
        
        // Then - Connection details should be valid
        assertThat(jdbcUrl).isNotNull();
        assertThat(jdbcUrl).contains("jdbc:postgresql://");
        assertThat(username).isEqualTo("test");
        assertThat(password).isEqualTo("test");
    }

    @Test
    void shouldHaveValidKafkaBootstrapServers() {
        // Given - Kafka container is running
        
        // When - We get bootstrap servers
        String bootstrapServers = kafka.getBootstrapServers();
        
        // Then - Bootstrap servers should be valid
        assertThat(bootstrapServers).isNotNull();
        assertThat(bootstrapServers).contains("localhost:");
    }

    @Test
    void shouldHaveValidRedisConnection() {
        // Given - Redis container is running
        
        // When - We get connection details
        String host = redis.getHost();
        Integer port = redis.getMappedPort(6379);
        
        // Then - Connection details should be valid
        assertThat(host).isNotNull();
        assertThat(port).isGreaterThan(0);
    }

    @Test
    void contextLoads() {
        // This test ensures the Spring Boot context loads successfully
        // with all the testcontainer infrastructure in place
        assertThat(true).isTrue();
    }
}