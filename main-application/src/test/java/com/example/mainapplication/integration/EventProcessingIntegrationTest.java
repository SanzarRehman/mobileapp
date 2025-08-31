package com.example.mainapplication.integration;

import com.example.mainapplication.event.UserCreatedEvent;
import com.example.mainapplication.event.UserUpdatedEvent;
import com.example.mainapplication.projection.UserProjection;
import com.example.mainapplication.query.FindUserByIdQuery;
import com.example.mainapplication.repository.UserProjectionRepository;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the complete event processing flow:
 * Event Gateway -> Event Handler -> Projection Update -> Query Handler
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=localhost:9092",
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class EventProcessingIntegrationTest {

    @Autowired
    private EventGateway eventGateway;

    @Autowired
    private QueryGateway queryGateway;

    @Autowired
    private UserProjectionRepository userProjectionRepository;

    @BeforeEach
    void setUp() {
        // Clean up any existing data
        userProjectionRepository.deleteAll();
    }

    @Test
    void shouldProcessUserCreatedEventAndCreateProjection() throws Exception {
        // Given
        String userId = "integration-test-user-1";
        Instant createdAt = Instant.now();
        UserCreatedEvent event = new UserCreatedEvent(
            userId,
            "integrationuser",
            "integration@example.com",
            "Integration Test User",
            createdAt
        );

        // When
        eventGateway.publish(event);

        // Wait for event processing
        Thread.sleep(1000);

        // Then - Check projection was created
        Optional<UserProjection> projection = userProjectionRepository.findById(userId);
        assertTrue(projection.isPresent());
        assertEquals("Integration Test User", projection.get().getName());
        assertEquals("integration@example.com", projection.get().getEmail());
        assertEquals("ACTIVE", projection.get().getStatus());

        // Then - Check query handler works
        CompletableFuture<Optional<UserProjection>> queryResult = 
            queryGateway.query(new FindUserByIdQuery(userId), 
                org.axonframework.messaging.responsetypes.ResponseTypes.optionalInstanceOf(UserProjection.class));
        
        Optional<UserProjection> queriedProjection = queryResult.get(5, TimeUnit.SECONDS);
        assertTrue(queriedProjection.isPresent());
        assertEquals(projection.get().getId(), queriedProjection.get().getId());
        assertEquals(projection.get().getName(), queriedProjection.get().getName());
    }

    @Test
    void shouldProcessUserUpdatedEventAndUpdateProjection() throws Exception {
        // Given - Create initial projection
        String userId = "integration-test-user-2";
        Instant createdAt = Instant.now();
        UserCreatedEvent createEvent = new UserCreatedEvent(
            userId,
            "originaluser",
            "original@example.com",
            "Original User",
            createdAt
        );
        
        eventGateway.publish(createEvent);
        Thread.sleep(500); // Wait for creation

        // When - Update the user
        Instant updatedAt = createdAt.plusSeconds(3600);
        UserUpdatedEvent updateEvent = new UserUpdatedEvent(
            userId,
            "updateduser",
            "updated@example.com",
            "Updated User",
            updatedAt
        );
        
        eventGateway.publish(updateEvent);
        Thread.sleep(1000); // Wait for update

        // Then - Check projection was updated
        Optional<UserProjection> projection = userProjectionRepository.findById(userId);
        assertTrue(projection.isPresent());
        assertEquals("Updated User", projection.get().getName());
        assertEquals("updated@example.com", projection.get().getEmail());
        assertEquals("ACTIVE", projection.get().getStatus()); // Status should remain unchanged

        // Then - Check query handler returns updated data
        CompletableFuture<Optional<UserProjection>> queryResult = 
            queryGateway.query(new FindUserByIdQuery(userId), 
                org.axonframework.messaging.responsetypes.ResponseTypes.optionalInstanceOf(UserProjection.class));
        
        Optional<UserProjection> queriedProjection = queryResult.get(5, TimeUnit.SECONDS);
        assertTrue(queriedProjection.isPresent());
        assertEquals("Updated User", queriedProjection.get().getName());
        assertEquals("updated@example.com", queriedProjection.get().getEmail());
    }

    @Test
    void shouldNotCreateDuplicateProjectionOnDuplicateEvent() throws Exception {
        // Given
        String userId = "integration-test-user-3";
        Instant createdAt = Instant.now();
        UserCreatedEvent event = new UserCreatedEvent(
            userId,
            "duplicateuser",
            "duplicate@example.com",
            "Duplicate Test User",
            createdAt
        );

        // When - Publish the same event twice
        eventGateway.publish(event);
        Thread.sleep(500);
        eventGateway.publish(event);
        Thread.sleep(1000);

        // Then - Should only have one projection
        long count = userProjectionRepository.count();
        assertEquals(1, count);

        Optional<UserProjection> projection = userProjectionRepository.findById(userId);
        assertTrue(projection.isPresent());
        assertEquals("Duplicate Test User", projection.get().getName());
    }

    @Test
    void shouldHandleUpdateEventForNonExistentUser() throws Exception {
        // Given
        String nonExistentUserId = "non-existent-user";
        Instant updatedAt = Instant.now();
        UserUpdatedEvent updateEvent = new UserUpdatedEvent(
            nonExistentUserId,
            "nonexistent",
            "nonexistent@example.com",
            "Non Existent User",
            updatedAt
        );

        // When & Then - Should not create a projection and should handle gracefully
        assertDoesNotThrow(() -> {
            eventGateway.publish(updateEvent);
            Thread.sleep(1000);
        });

        // Verify no projection was created
        Optional<UserProjection> projection = userProjectionRepository.findById(nonExistentUserId);
        assertFalse(projection.isPresent());
    }

    @Test
    void shouldProcessMultipleEventsInSequence() throws Exception {
        // Given
        String userId1 = "integration-test-user-4";
        String userId2 = "integration-test-user-5";
        Instant now = Instant.now();

        UserCreatedEvent event1 = new UserCreatedEvent(userId1, "user1", "user1@example.com", "User One", now);
        UserCreatedEvent event2 = new UserCreatedEvent(userId2, "user2", "user2@example.com", "User Two", now.plusSeconds(1));

        // When
        eventGateway.publish(event1);
        eventGateway.publish(event2);
        Thread.sleep(1500);

        // Then
        assertEquals(2, userProjectionRepository.count());
        
        Optional<UserProjection> projection1 = userProjectionRepository.findById(userId1);
        Optional<UserProjection> projection2 = userProjectionRepository.findById(userId2);
        
        assertTrue(projection1.isPresent());
        assertTrue(projection2.isPresent());
        assertEquals("User One", projection1.get().getName());
        assertEquals("User Two", projection2.get().getName());
    }
}