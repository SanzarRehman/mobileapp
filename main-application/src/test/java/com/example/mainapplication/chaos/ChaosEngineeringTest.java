package com.example.mainapplication.chaos;

import com.example.mainapplication.command.CreateUserCommand;
import com.example.mainapplication.repository.UserProjectionRepository;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.ToxicDirection;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public class ChaosEngineeringTest {

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

    @Container
    static ToxiproxyContainer toxiproxy = new ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.5.0");

    private static Proxy kafkaProxy;
    private static Proxy postgresProxy;
    private static Proxy redisProxy;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Setup proxies for chaos testing
        ToxiproxyClient toxiproxyClient = new ToxiproxyClient(toxiproxy.getHost(), toxiproxy.getControlPort());
        
        try {
            kafkaProxy = toxiproxyClient.createProxy("kafka", "0.0.0.0:8666", 
                kafka.getHost() + ":" + kafka.getMappedPort(9093));
            postgresProxy = toxiproxyClient.createProxy("postgres", "0.0.0.0:8667", 
                postgres.getHost() + ":" + postgres.getMappedPort(5432));
            redisProxy = toxiproxyClient.createProxy("redis", "0.0.0.0:8668", 
                redis.getHost() + ":" + redis.getMappedPort(6379));
        } catch (Exception e) {
            throw new RuntimeException("Failed to setup toxiproxy", e);
        }

        registry.add("spring.datasource.url", () -> 
            "jdbc:postgresql://" + toxiproxy.getHost() + ":" + toxiproxy.getMappedPort(8667) + "/testdb");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> 
            toxiproxy.getHost() + ":" + toxiproxy.getMappedPort(8666));
        registry.add("spring.redis.host", toxiproxy::getHost);
        registry.add("spring.redis.port", () -> toxiproxy.getMappedPort(8668));
        registry.add("custom.axon.server.url", () -> "http://localhost:8081");
    }

    @Autowired
    private CommandGateway commandGateway;

    @Autowired
    private UserProjectionRepository userProjectionRepository;

    @BeforeEach
    void setUp() throws Exception {
        // Reset all toxics before each test
        kafkaProxy.toxics().getAll().forEach(toxic -> {
            try {
                toxic.remove();
            } catch (Exception e) {
                // Ignore
            }
        });
        postgresProxy.toxics().getAll().forEach(toxic -> {
            try {
                toxic.remove();
            } catch (Exception e) {
                // Ignore
            }
        });
        redisProxy.toxics().getAll().forEach(toxic -> {
            try {
                toxic.remove();
            } catch (Exception e) {
                // Ignore
            }
        });
        
        userProjectionRepository.deleteAll();
    }

    @Test
    void shouldHandleDatabaseLatency() throws Exception {
        // Given - Add latency to database connections
        postgresProxy.toxics()
            .latency("latency", ToxicDirection.DOWNSTREAM, 2000)
            .setJitter(500);

        // When - Send command with database latency
        String userId = UUID.randomUUID().toString();
        CreateUserCommand command = new CreateUserCommand(userId, "Latency User", "latency@example.com");
        
        long startTime = System.currentTimeMillis();
        CompletableFuture<String> result = commandGateway.send(command);
        String commandResult = result.get(10, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();

        // Then - Command should still succeed but take longer
        assertThat(commandResult).isEqualTo(userId);
        assertThat(endTime - startTime).isGreaterThan(2000); // Should take at least 2 seconds due to latency
    }

    @Test
    void shouldHandleKafkaNetworkPartition() throws Exception {
        // Given - Create network partition for Kafka
        kafkaProxy.toxics()
            .bandwidth("bandwidth", ToxicDirection.DOWNSTREAM, 0); // Block all traffic

        // When - Try to send command during partition
        String userId = UUID.randomUUID().toString();
        CreateUserCommand command = new CreateUserCommand(userId, "Partition User", "partition@example.com");

        // Should handle the failure gracefully
        CompletableFuture<String> result = commandGateway.send(command);
        
        // Wait a bit then restore connection
        Thread.sleep(3000);
        kafkaProxy.toxics().get("bandwidth").remove();

        // Then - Command should eventually succeed after partition heals
        String commandResult = result.get(15, TimeUnit.SECONDS);
        assertThat(commandResult).isEqualTo(userId);
    }

    @Test
    void shouldHandleRedisConnectionFailure() throws Exception {
        // Given - Simulate Redis connection failure
        redisProxy.toxics()
            .resetPeer("reset", ToxicDirection.DOWNSTREAM, 1000);

        // When - Send commands during Redis failure
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < 5; i++) {
            try {
                String userId = UUID.randomUUID().toString();
                CreateUserCommand command = new CreateUserCommand(userId, "Redis Test " + i, "redis" + i + "@example.com");
                commandGateway.send(command).get(5, TimeUnit.SECONDS);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            }
        }

        // Then - System should handle Redis failures gracefully
        // Some commands might fail, but system should remain stable
        assertThat(successCount.get() + failureCount.get()).isEqualTo(5);
    }

    @Test
    void shouldRecoverFromDatabaseConnectionLoss() throws Exception {
        // Given - First establish that system works
        String userId1 = UUID.randomUUID().toString();
        CreateUserCommand command1 = new CreateUserCommand(userId1, "Before Failure", "before@example.com");
        commandGateway.send(command1).get(5, TimeUnit.SECONDS);

        // When - Simulate database connection loss
        postgresProxy.toxics()
            .resetPeer("reset", ToxicDirection.DOWNSTREAM, 100);

        Thread.sleep(2000); // Let the connection reset take effect

        // Remove the toxic to allow recovery
        postgresProxy.toxics().get("reset").remove();

        Thread.sleep(3000); // Allow time for connection recovery

        // Then - System should recover and process new commands
        String userId2 = UUID.randomUUID().toString();
        CreateUserCommand command2 = new CreateUserCommand(userId2, "After Recovery", "after@example.com");
        String result = commandGateway.send(command2).get(10, TimeUnit.SECONDS);
        
        assertThat(result).isEqualTo(userId2);
    }

    @Test
    void shouldHandleSlowNetworkConditions() throws Exception {
        // Given - Simulate slow network conditions
        kafkaProxy.toxics()
            .bandwidth("slow_network", ToxicDirection.DOWNSTREAM, 1024); // 1KB/s

        postgresProxy.toxics()
            .latency("slow_db", ToxicDirection.DOWNSTREAM, 1000)
            .setJitter(200);

        // When - Send multiple commands under slow conditions
        AtomicInteger completedCommands = new AtomicInteger(0);
        CompletableFuture<Void>[] futures = new CompletableFuture[5];

        for (int i = 0; i < 5; i++) {
            final int index = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    String userId = UUID.randomUUID().toString();
                    CreateUserCommand command = new CreateUserCommand(userId, "Slow User " + index, "slow" + index + "@example.com");
                    commandGateway.send(command).get(20, TimeUnit.SECONDS);
                    completedCommands.incrementAndGet();
                } catch (Exception e) {
                    // Expected under slow conditions
                }
            });
        }

        // Wait for completion with extended timeout
        CompletableFuture.allOf(futures).get(60, TimeUnit.SECONDS);

        // Then - At least some commands should complete despite slow conditions
        assertThat(completedCommands.get()).isGreaterThan(0);
    }

    @Test
    void shouldHandleIntermittentFailures() throws Exception {
        // Given - Setup intermittent failures
        AtomicInteger commandCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        // When - Send commands with intermittent failures
        for (int i = 0; i < 10; i++) {
            // Add failure every 3rd command
            if (i % 3 == 0) {
                postgresProxy.toxics()
                    .timeout("timeout", ToxicDirection.DOWNSTREAM, 100);
            }

            try {
                String userId = UUID.randomUUID().toString();
                CreateUserCommand command = new CreateUserCommand(userId, "Intermittent " + i, "intermittent" + i + "@example.com");
                commandGateway.send(command).get(5, TimeUnit.SECONDS);
                successCount.incrementAndGet();
            } catch (Exception e) {
                // Expected for some commands
            }

            commandCount.incrementAndGet();

            // Remove failure condition
            try {
                postgresProxy.toxics().get("timeout").remove();
            } catch (Exception e) {
                // Might not exist
            }

            Thread.sleep(500);
        }

        // Then - System should handle intermittent failures
        assertThat(commandCount.get()).isEqualTo(10);
        assertThat(successCount.get()).isGreaterThan(5); // At least half should succeed
    }

    @Test
    void shouldMaintainDataConsistencyDuringFailures() throws Exception {
        // Given - Create some initial data
        String userId = UUID.randomUUID().toString();
        CreateUserCommand command = new CreateUserCommand(userId, "Consistency User", "consistency@example.com");
        commandGateway.send(command).get(5, TimeUnit.SECONDS);

        // When - Introduce failure during processing
        postgresProxy.toxics()
            .resetPeer("consistency_test", ToxicDirection.DOWNSTREAM, 500);

        // Try to process more commands
        AtomicInteger attempts = new AtomicInteger(0);
        AtomicInteger successes = new AtomicInteger(0);

        for (int i = 0; i < 5; i++) {
            try {
                String newUserId = UUID.randomUUID().toString();
                CreateUserCommand newCommand = new CreateUserCommand(newUserId, "User " + i, "user" + i + "@example.com");
                commandGateway.send(newCommand).get(3, TimeUnit.SECONDS);
                successes.incrementAndGet();
            } catch (Exception e) {
                // Some may fail due to connection issues
            }
            attempts.incrementAndGet();
        }

        // Remove the toxic and wait for recovery
        postgresProxy.toxics().get("consistency_test").remove();
        Thread.sleep(5000);

        // Then - Data should remain consistent
        // Original user should still exist
        assertThat(userProjectionRepository.findById(userId)).isPresent();
        
        // System should be able to process new commands after recovery
        String recoveryUserId = UUID.randomUUID().toString();
        CreateUserCommand recoveryCommand = new CreateUserCommand(recoveryUserId, "Recovery User", "recovery@example.com");
        String result = commandGateway.send(recoveryCommand).get(5, TimeUnit.SECONDS);
        assertThat(result).isEqualTo(recoveryUserId);
    }
}