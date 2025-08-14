package com.example.mainapplication.aggregate;

import com.example.mainapplication.command.CreateUserCommand;
import com.example.mainapplication.command.UpdateUserCommand;
import com.example.mainapplication.event.UserCreatedEvent;
import com.example.mainapplication.event.UserUpdatedEvent;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UserAggregate business logic.
 * Tests command handling, event sourcing, and business rule validation.
 */
class UserAggregateTest {

    private FixtureConfiguration<UserAggregate> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(UserAggregate.class);
    }

    @Nested
    @DisplayName("User Creation Tests")
    class UserCreationTests {

        @Test
        @DisplayName("Should create user successfully with valid data")
        void shouldCreateUserSuccessfully() {
            String userId = "user-123";
            String username = "john_doe";
            String email = "john.doe@example.com";
            String fullName = "John Doe";

            CreateUserCommand command = new CreateUserCommand(userId, username, email, fullName);

            fixture.givenNoPriorActivity()
                    .when(command)
                    .expectSuccessfulHandlerExecution()
                    .expectState(aggregate -> {
                        assertEquals(userId, aggregate.getUserId());
                        assertEquals(username, aggregate.getUsername());
                        assertEquals(email, aggregate.getEmail());
                        assertEquals(fullName, aggregate.getFullName());
                        assertTrue(aggregate.isActive());
                        assertNotNull(aggregate.getCreatedAt());
                        assertNotNull(aggregate.getUpdatedAt());
                    });
        }

        @Test
        @DisplayName("Should reject user creation with null username")
        void shouldRejectNullUsername() {
            assertThrows(NullPointerException.class, () -> 
                new CreateUserCommand("user-123", null, "john@example.com", "John Doe"));
        }

        @Test
        @DisplayName("Should reject user creation with empty username")
        void shouldRejectEmptyUsername() {
            CreateUserCommand command = new CreateUserCommand("user-123", "", "john@example.com", "John Doe");

            fixture.givenNoPriorActivity()
                    .when(command)
                    .expectException(IllegalArgumentException.class)
                    .expectExceptionMessage("Username cannot be null or empty");
        }

        @Test
        @DisplayName("Should reject user creation with short username")
        void shouldRejectShortUsername() {
            CreateUserCommand command = new CreateUserCommand("user-123", "jo", "john@example.com", "John Doe");

            fixture.givenNoPriorActivity()
                    .when(command)
                    .expectException(IllegalArgumentException.class)
                    .expectExceptionMessage("Username must be at least 3 characters long");
        }

        @Test
        @DisplayName("Should reject user creation with long username")
        void shouldRejectLongUsername() {
            String longUsername = "a".repeat(51);
            CreateUserCommand command = new CreateUserCommand("user-123", longUsername, "john@example.com", "John Doe");

            fixture.givenNoPriorActivity()
                    .when(command)
                    .expectException(IllegalArgumentException.class)
                    .expectExceptionMessage("Username cannot exceed 50 characters");
        }

        @Test
        @DisplayName("Should reject user creation with invalid username characters")
        void shouldRejectInvalidUsernameCharacters() {
            CreateUserCommand command = new CreateUserCommand("user-123", "john-doe!", "john@example.com", "John Doe");

            fixture.givenNoPriorActivity()
                    .when(command)
                    .expectException(IllegalArgumentException.class)
                    .expectExceptionMessage("Username can only contain letters, numbers, and underscores");
        }

        @Test
        @DisplayName("Should reject user creation with invalid email")
        void shouldRejectInvalidEmail() {
            CreateUserCommand command = new CreateUserCommand("user-123", "john_doe", "invalid-email", "John Doe");

            fixture.givenNoPriorActivity()
                    .when(command)
                    .expectException(IllegalArgumentException.class)
                    .expectExceptionMessage("Invalid email format");
        }

        @Test
        @DisplayName("Should reject user creation with null email")
        void shouldRejectNullEmail() {
            assertThrows(NullPointerException.class, () -> 
                new CreateUserCommand("user-123", "john_doe", null, "John Doe"));
        }

        @Test
        @DisplayName("Should reject user creation with long email")
        void shouldRejectLongEmail() {
            String longEmail = "a".repeat(250) + "@example.com";
            CreateUserCommand command = new CreateUserCommand("user-123", "john_doe", longEmail, "John Doe");

            fixture.givenNoPriorActivity()
                    .when(command)
                    .expectException(IllegalArgumentException.class)
                    .expectExceptionMessage("Email cannot exceed 255 characters");
        }

        @Test
        @DisplayName("Should reject user creation with null full name")
        void shouldRejectNullFullName() {
            assertThrows(NullPointerException.class, () -> 
                new CreateUserCommand("user-123", "john_doe", "john@example.com", null));
        }

        @Test
        @DisplayName("Should reject user creation with short full name")
        void shouldRejectShortFullName() {
            CreateUserCommand command = new CreateUserCommand("user-123", "john_doe", "john@example.com", "J");

            fixture.givenNoPriorActivity()
                    .when(command)
                    .expectException(IllegalArgumentException.class)
                    .expectExceptionMessage("Full name must be at least 2 characters long");
        }

        @Test
        @DisplayName("Should reject user creation with long full name")
        void shouldRejectLongFullName() {
            String longFullName = "a".repeat(101);
            CreateUserCommand command = new CreateUserCommand("user-123", "john_doe", "john@example.com", longFullName);

            fixture.givenNoPriorActivity()
                    .when(command)
                    .expectException(IllegalArgumentException.class)
                    .expectExceptionMessage("Full name cannot exceed 100 characters");
        }
    }

    @Nested
    @DisplayName("User Update Tests")
    class UserUpdateTests {

        @Test
        @DisplayName("Should update user successfully with valid data")
        void shouldUpdateUserSuccessfully() {
            String userId = "user-123";
            String originalUsername = "john_doe";
            String originalEmail = "john.doe@example.com";
            String originalFullName = "John Doe";
            
            String newUsername = "john_smith";
            String newEmail = "john.smith@example.com";
            String newFullName = "John Smith";

            UserCreatedEvent createdEvent = new UserCreatedEvent(userId, originalUsername, originalEmail, originalFullName, Instant.now());
            UpdateUserCommand updateCommand = new UpdateUserCommand(userId, newUsername, newEmail, newFullName);

            fixture.given(createdEvent)
                    .when(updateCommand)
                    .expectSuccessfulHandlerExecution()
                    .expectState(aggregate -> {
                        assertEquals(userId, aggregate.getUserId());
                        assertEquals(newUsername, aggregate.getUsername());
                        assertEquals(newEmail, aggregate.getEmail());
                        assertEquals(newFullName, aggregate.getFullName());
                        assertTrue(aggregate.isActive());
                    });
        }

        @Test
        @DisplayName("Should not publish event when no changes are made")
        void shouldNotPublishEventWhenNoChanges() {
            String userId = "user-123";
            String username = "john_doe";
            String email = "john.doe@example.com";
            String fullName = "John Doe";

            UserCreatedEvent createdEvent = new UserCreatedEvent(userId, username, email, fullName, Instant.now());
            UpdateUserCommand updateCommand = new UpdateUserCommand(userId, username, email, fullName);

            fixture.given(createdEvent)
                    .when(updateCommand)
                    .expectNoEvents();
        }

        @Test
        @DisplayName("Should reject update with invalid username")
        void shouldRejectUpdateWithInvalidUsername() {
            String userId = "user-123";
            UserCreatedEvent createdEvent = new UserCreatedEvent(userId, "john_doe", "john@example.com", "John Doe", Instant.now());
            UpdateUserCommand updateCommand = new UpdateUserCommand(userId, "jo", "john@example.com", "John Doe");

            fixture.given(createdEvent)
                    .when(updateCommand)
                    .expectException(IllegalArgumentException.class)
                    .expectExceptionMessage("Username must be at least 3 characters long");
        }

        @Test
        @DisplayName("Should reject update with invalid email")
        void shouldRejectUpdateWithInvalidEmail() {
            String userId = "user-123";
            UserCreatedEvent createdEvent = new UserCreatedEvent(userId, "john_doe", "john@example.com", "John Doe", Instant.now());
            UpdateUserCommand updateCommand = new UpdateUserCommand(userId, "john_doe", "invalid-email", "John Doe");

            fixture.given(createdEvent)
                    .when(updateCommand)
                    .expectException(IllegalArgumentException.class)
                    .expectExceptionMessage("Invalid email format");
        }

        @Test
        @DisplayName("Should reject update with invalid full name")
        void shouldRejectUpdateWithInvalidFullName() {
            String userId = "user-123";
            UserCreatedEvent createdEvent = new UserCreatedEvent(userId, "john_doe", "john@example.com", "John Doe", Instant.now());
            UpdateUserCommand updateCommand = new UpdateUserCommand(userId, "john_doe", "john@example.com", "J");

            fixture.given(createdEvent)
                    .when(updateCommand)
                    .expectException(IllegalArgumentException.class)
                    .expectExceptionMessage("Full name must be at least 2 characters long");
        }
    }

    @Nested
    @DisplayName("Event Sourcing Tests")
    class EventSourcingTests {

        @Test
        @DisplayName("Should properly reconstruct aggregate state from events")
        void shouldReconstructAggregateStateFromEvents() {
            String userId = "user-123";
            String originalUsername = "john_doe";
            String originalEmail = "john.doe@example.com";
            String originalFullName = "John Doe";
            Instant createdAt = Instant.now().minusSeconds(3600);
            
            String updatedUsername = "john_smith";
            String updatedEmail = "john.smith@example.com";
            String updatedFullName = "John Smith";
            Instant updatedAt = Instant.now();

            UserCreatedEvent createdEvent = new UserCreatedEvent(userId, originalUsername, originalEmail, originalFullName, createdAt);
            UserUpdatedEvent updatedEvent = new UserUpdatedEvent(userId, updatedUsername, updatedEmail, updatedFullName, updatedAt);

            // Test that the aggregate state is properly reconstructed from the events
            // The final command should see the state after both events have been applied
            fixture.given(createdEvent, updatedEvent)
                    .when(new UpdateUserCommand(userId, "jane_doe", "jane@example.com", "Jane Doe"))
                    .expectState(aggregate -> {
                        assertEquals(userId, aggregate.getUserId());
                        // After the new update command, the state should reflect the new values
                        assertEquals("jane_doe", aggregate.getUsername());
                        assertEquals("jane@example.com", aggregate.getEmail());
                        assertEquals("Jane Doe", aggregate.getFullName());
                        assertTrue(aggregate.isActive());
                        assertEquals(createdAt, aggregate.getCreatedAt());
                        // The updated timestamp should be more recent than the previous update
                        assertTrue(aggregate.getUpdatedAt().isAfter(updatedAt));
                    });
        }
    }
}