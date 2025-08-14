package com.example.mainapplication.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UserCreatedEvent.
 */
class UserCreatedEventTest {

    @Test
    @DisplayName("Should create event successfully with valid parameters")
    void shouldCreateEventSuccessfully() {
        String userId = "user-123";
        String username = "john_doe";
        String email = "john.doe@example.com";
        String fullName = "John Doe";
        Instant createdAt = Instant.now();

        UserCreatedEvent event = new UserCreatedEvent(userId, username, email, fullName, createdAt);

        assertEquals(userId, event.getUserId());
        assertEquals(username, event.getUsername());
        assertEquals(email, event.getEmail());
        assertEquals(fullName, event.getFullName());
        assertEquals(createdAt, event.getCreatedAt());
    }

    @Test
    @DisplayName("Should throw exception when userId is null")
    void shouldThrowExceptionWhenUserIdIsNull() {
        assertThrows(NullPointerException.class, () -> 
            new UserCreatedEvent(null, "john_doe", "john@example.com", "John Doe", Instant.now()));
    }

    @Test
    @DisplayName("Should throw exception when username is null")
    void shouldThrowExceptionWhenUsernameIsNull() {
        assertThrows(NullPointerException.class, () -> 
            new UserCreatedEvent("user-123", null, "john@example.com", "John Doe", Instant.now()));
    }

    @Test
    @DisplayName("Should throw exception when email is null")
    void shouldThrowExceptionWhenEmailIsNull() {
        assertThrows(NullPointerException.class, () -> 
            new UserCreatedEvent("user-123", "john_doe", null, "John Doe", Instant.now()));
    }

    @Test
    @DisplayName("Should throw exception when fullName is null")
    void shouldThrowExceptionWhenFullNameIsNull() {
        assertThrows(NullPointerException.class, () -> 
            new UserCreatedEvent("user-123", "john_doe", "john@example.com", null, Instant.now()));
    }

    @Test
    @DisplayName("Should throw exception when createdAt is null")
    void shouldThrowExceptionWhenCreatedAtIsNull() {
        assertThrows(NullPointerException.class, () -> 
            new UserCreatedEvent("user-123", "john_doe", "john@example.com", "John Doe", null));
    }

    @Test
    @DisplayName("Should implement equals and hashCode correctly")
    void shouldImplementEqualsAndHashCodeCorrectly() {
        Instant timestamp = Instant.now();
        UserCreatedEvent event1 = new UserCreatedEvent("user-123", "john_doe", "john@example.com", "John Doe", timestamp);
        UserCreatedEvent event2 = new UserCreatedEvent("user-123", "john_doe", "john@example.com", "John Doe", timestamp);
        UserCreatedEvent event3 = new UserCreatedEvent("user-456", "jane_doe", "jane@example.com", "Jane Doe", timestamp);

        assertEquals(event1, event2);
        assertEquals(event1.hashCode(), event2.hashCode());
        assertNotEquals(event1, event3);
        assertNotEquals(event1.hashCode(), event3.hashCode());
    }

    @Test
    @DisplayName("Should implement toString correctly")
    void shouldImplementToStringCorrectly() {
        Instant timestamp = Instant.now();
        UserCreatedEvent event = new UserCreatedEvent("user-123", "john_doe", "john@example.com", "John Doe", timestamp);
        String toString = event.toString();

        assertTrue(toString.contains("user-123"));
        assertTrue(toString.contains("john_doe"));
        assertTrue(toString.contains("john@example.com"));
        assertTrue(toString.contains("John Doe"));
        assertTrue(toString.contains(timestamp.toString()));
    }
}