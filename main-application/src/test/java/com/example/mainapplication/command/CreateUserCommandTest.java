package com.example.mainapplication.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CreateUserCommand.
 */
class CreateUserCommandTest {

    @Test
    @DisplayName("Should create command successfully with valid parameters")
    void shouldCreateCommandSuccessfully() {
        String userId = "user-123";
        String username = "john_doe";
        String email = "john.doe@example.com";
        String fullName = "John Doe";

        CreateUserCommand command = new CreateUserCommand(userId, username, email, fullName);

        assertEquals(userId, command.getUserId());
        assertEquals(username, command.getUsername());
        assertEquals(email, command.getEmail());
        assertEquals(fullName, command.getFullName());
    }

    @Test
    @DisplayName("Should throw exception when userId is null")
    void shouldThrowExceptionWhenUserIdIsNull() {
        assertThrows(NullPointerException.class, () -> 
            new CreateUserCommand(null, "john_doe", "john@example.com", "John Doe"));
    }

    @Test
    @DisplayName("Should throw exception when username is null")
    void shouldThrowExceptionWhenUsernameIsNull() {
        assertThrows(NullPointerException.class, () -> 
            new CreateUserCommand("user-123", null, "john@example.com", "John Doe"));
    }

    @Test
    @DisplayName("Should throw exception when email is null")
    void shouldThrowExceptionWhenEmailIsNull() {
        assertThrows(NullPointerException.class, () -> 
            new CreateUserCommand("user-123", "john_doe", null, "John Doe"));
    }

    @Test
    @DisplayName("Should throw exception when fullName is null")
    void shouldThrowExceptionWhenFullNameIsNull() {
        assertThrows(NullPointerException.class, () -> 
            new CreateUserCommand("user-123", "john_doe", "john@example.com", null));
    }

    @Test
    @DisplayName("Should implement equals and hashCode correctly")
    void shouldImplementEqualsAndHashCodeCorrectly() {
        CreateUserCommand command1 = new CreateUserCommand("user-123", "john_doe", "john@example.com", "John Doe");
        CreateUserCommand command2 = new CreateUserCommand("user-123", "john_doe", "john@example.com", "John Doe");
        CreateUserCommand command3 = new CreateUserCommand("user-456", "jane_doe", "jane@example.com", "Jane Doe");

        assertEquals(command1, command2);
        assertEquals(command1.hashCode(), command2.hashCode());
        assertNotEquals(command1, command3);
        assertNotEquals(command1.hashCode(), command3.hashCode());
    }

    @Test
    @DisplayName("Should implement toString correctly")
    void shouldImplementToStringCorrectly() {
        CreateUserCommand command = new CreateUserCommand("user-123", "john_doe", "john@example.com", "John Doe");
        String toString = command.toString();

        assertTrue(toString.contains("user-123"));
        assertTrue(toString.contains("john_doe"));
        assertTrue(toString.contains("john@example.com"));
        assertTrue(toString.contains("John Doe"));
    }
}