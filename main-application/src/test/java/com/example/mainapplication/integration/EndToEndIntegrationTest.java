package com.example.mainapplication.integration;

import com.example.mainapplication.command.CreateUserCommand;
import com.example.mainapplication.command.UpdateUserCommand;
import com.example.mainapplication.dto.CreateUserRequest;
import com.example.mainapplication.dto.UpdateUserRequest;
import com.example.mainapplication.projection.UserProjection;
import com.example.mainapplication.query.FindUserByIdQuery;
import com.example.mainapplication.repository.UserProjectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Testcontainers
@Transactional
public class EndToEndIntegrationTest {

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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommandGateway commandGateway;

    @Autowired
    private QueryGateway queryGateway;

    @Autowired
    private UserProjectionRepository userProjectionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID().toString();
        userProjectionRepository.deleteAll();
    }

    @Test
    void shouldCompleteFullCommandQueryFlow() throws Exception {
        // Given
        CreateUserRequest createRequest = new CreateUserRequest();
        createRequest.setName("John Doe");
        createRequest.setEmail("john.doe@example.com");

        // When - Create user via REST API
        String response = mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Extract user ID from response
        String createdUserId = objectMapper.readTree(response).get("aggregateId").asText();

        // Wait for event processing
        Thread.sleep(2000);

        // Then - Verify user was created and projection updated
        UserProjection projection = userProjectionRepository.findById(createdUserId).orElse(null);
        assertThat(projection).isNotNull();
        assertThat(projection.getName()).isEqualTo("John Doe");
        assertThat(projection.getEmail()).isEqualTo("john.doe@example.com");

        // When - Query user via REST API
        mockMvc.perform(get("/api/users/" + createdUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdUserId))
                .andExpect(jsonPath("$.name").value("John Doe"))
                .andExpect(jsonPath("$.email").value("john.doe@example.com"));

        // When - Update user via REST API
        UpdateUserRequest updateRequest = new UpdateUserRequest();
        updateRequest.setName("Jane Doe");
        updateRequest.setEmail("jane.doe@example.com");

        mockMvc.perform(put("/api/users/" + createdUserId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isAccepted());

        // Wait for event processing
        Thread.sleep(2000);

        // Then - Verify user was updated
        projection = userProjectionRepository.findById(createdUserId).orElse(null);
        assertThat(projection).isNotNull();
        assertThat(projection.getName()).isEqualTo("Jane Doe");
        assertThat(projection.getEmail()).isEqualTo("jane.doe@example.com");
    }

    @Test
    void shouldHandleCommandGatewayDirectly() throws Exception {
        // Given
        CreateUserCommand command = new CreateUserCommand(userId, "Direct User", "direct@example.com");

        // When
        CompletableFuture<String> result = commandGateway.send(command);
        String commandResult = result.get(5, TimeUnit.SECONDS);

        // Then
        assertThat(commandResult).isEqualTo(userId);

        // Wait for event processing
        Thread.sleep(2000);

        // Verify projection was updated
        UserProjection projection = userProjectionRepository.findById(userId).orElse(null);
        assertThat(projection).isNotNull();
        assertThat(projection.getName()).isEqualTo("Direct User");
        assertThat(projection.getEmail()).isEqualTo("direct@example.com");
    }

    @Test
    void shouldHandleQueryGatewayDirectly() throws Exception {
        // Given - Create user first
        CreateUserCommand command = new CreateUserCommand(userId, "Query User", "query@example.com");
        commandGateway.send(command).get(5, TimeUnit.SECONDS);
        Thread.sleep(2000);

        // When
        CompletableFuture<UserProjection> result = queryGateway.query(
                new FindUserByIdQuery(userId), UserProjection.class);
        UserProjection user = result.get(5, TimeUnit.SECONDS);

        // Then
        assertThat(user).isNotNull();
        assertThat(user.getName()).isEqualTo("Query User");
        assertThat(user.getEmail()).isEqualTo("query@example.com");
    }

    @Test
    void shouldHandleConcurrentCommands() throws Exception {
        // Given
        int numberOfCommands = 10;
        CompletableFuture<String>[] futures = new CompletableFuture[numberOfCommands];

        // When - Send multiple commands concurrently
        for (int i = 0; i < numberOfCommands; i++) {
            String id = UUID.randomUUID().toString();
            CreateUserCommand command = new CreateUserCommand(id, "User " + i, "user" + i + "@example.com");
            futures[i] = commandGateway.send(command);
        }

        // Wait for all commands to complete
        CompletableFuture.allOf(futures).get(10, TimeUnit.SECONDS);

        // Wait for event processing
        Thread.sleep(3000);

        // Then - Verify all users were created
        long userCount = userProjectionRepository.count();
        assertThat(userCount).isEqualTo(numberOfCommands);
    }

    @Test
    void shouldMaintainEventOrderingForSameAggregate() throws Exception {
        // Given
        CreateUserCommand createCommand = new CreateUserCommand(userId, "Original User", "original@example.com");
        
        // When - Send create command
        commandGateway.send(createCommand).get(5, TimeUnit.SECONDS);
        Thread.sleep(1000);

        // Send multiple update commands in sequence
        for (int i = 1; i <= 5; i++) {
            UpdateUserCommand updateCommand = new UpdateUserCommand(userId, "User " + i, "user" + i + "@example.com");
            commandGateway.send(updateCommand).get(5, TimeUnit.SECONDS);
            Thread.sleep(500);
        }

        // Wait for all events to be processed
        Thread.sleep(3000);

        // Then - Verify final state reflects last update
        UserProjection projection = userProjectionRepository.findById(userId).orElse(null);
        assertThat(projection).isNotNull();
        assertThat(projection.getName()).isEqualTo("User 5");
        assertThat(projection.getEmail()).isEqualTo("user5@example.com");
    }
}