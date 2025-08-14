package com.example.mainapplication.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UpdateUserCommand.
 */
class UpdateUserCommandTest {

    @Test
    @DisplayName("Should create command successfully with valid parameters")
    void shouldCreateCommandSuccessfully() {
        String userId = "user-123";
        String username = "john_doe";
        String email = "john.doe@example.com";
        String fullName = "John Doe";

        UpdateUserCommand command = new UpdateUserCommand(userId, username, email, fullName);

        assertEquals(userId, command.getUserId());
        assertEquals(username, command.getUsername());
        assertEquals(email, command.getEmail());
        assertEquals(fullName, command.getFullName());
    }

    @Test
    @DisplayName("Should throw exception when userId is null")
    void shouldThrowExceptionWhenUserIdIsNull() {
        assertThrows(NullPointerException.class, () -> 
            new UpdateUserCommand(null, "john_doe", "john@example.com", "John Doe"));
    }

    @Test
    @DisplayName("Should throw exception when username is null")
    void shouldThrowExceptionWhenUsernameIsNull() {
        assertThrows(NullPointerException.class, () -> 
            new UpdateUserCommand("user-123", null, "john@example.com", "John Doe"));
    }

    @Test
    @DisplayName("Should throw exception when email is null")
    void shouldThrowExceptionWhenEmailIsNull() {
        assertThrows(NullPointerException.class, () -> 
            new UpdateUserCommand("user-123", "john_doe", null, "John Doe"));
    }

    @Test
    @DisplayName("Should throw exception when fullName is null")
    void shouldThrowExceptionWhenFullNameIsNull() {
        assertThrows(NullPointerException.class, () -> 
            new UpdateUserCommand("user-123", "john_doe", "john@example.com", null));
    }

    @Test
    @DisplayName("Should implement equals and hashCode correctly")
    void shouldImplementEqualsAndHashCodeCorrectly() {
        UpdateUserCommand command1 = new UpdateUserCommand("user-123", "john_doe", "john@example.com", "John Doe");
        UpdateUserCommand command2 = new UpdateUserCommand("user-123", "john_doe", "john@example.com", "John Doe");
        UpdateUserCommand command3 = new UpdateUserCommand("user-456", "jane_doe", "jane@example.com", "Jane Doe");

        assertEquals(command1, command2);
        assertEquals(command1.hashCode(), command2.hashCode());
        assertNotEquals(command1, command3);
        assertNotEquals(command1.hashCode(), command3.hashCode());
    }

    @Test
    @DisplayName("Should implement toString correctly")
    void shouldImplementToStringCorrectly() {
        UpdateUserCommand command = new UpdateUserCommand("user-123", "john_doe", "john@example.com", "John Doe");
        String toString = command.toString();

        assertTrue(toString.contains("user-123"));
        assertTrue(toString.contains("john_doe"));
        assertTrue(toString.contains("john@example.com"));
        assertTrue(toString.contains("John Doe"));
    }
}