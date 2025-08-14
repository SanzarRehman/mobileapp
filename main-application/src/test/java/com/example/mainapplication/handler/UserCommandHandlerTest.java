package com.example.mainapplication.handler;

import com.example.mainapplication.command.CreateUserCommand;
import com.example.mainapplication.command.UpdateUserCommand;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserCommandHandler.
 */
@ExtendWith(MockitoExtension.class)
class UserCommandHandlerTest {

    @Mock
    private CommandGateway commandGateway;

    private UserCommandHandler commandHandler;

    @BeforeEach
    void setUp() {
        commandHandler = new UserCommandHandler(commandGateway);
    }

    @Test
    void handle_CreateUserCommand_ShouldDelegateToCommandGateway() {
        // Given
        CreateUserCommand command = new CreateUserCommand(
                "user-123",
                "testuser",
                "test@example.com",
                "Test User"
        );
        CompletableFuture<String> expectedResult = CompletableFuture.completedFuture("user-123");
        when(commandGateway.<String>send(command)).thenReturn(expectedResult);

        // When
        CompletableFuture<String> result = commandHandler.handle(command);

        // Then
        assertNotNull(result);
        assertEquals(expectedResult, result);
        verify(commandGateway).send(command);
    }

    @Test
    void handle_UpdateUserCommand_ShouldDelegateToCommandGateway() {
        // Given
        UpdateUserCommand command = new UpdateUserCommand(
                "user-123",
                "updateduser",
                "updated@example.com",
                "Updated User"
        );
        CompletableFuture<Void> expectedResult = CompletableFuture.completedFuture(null);
        when(commandGateway.<Void>send(command)).thenReturn(expectedResult);

        // When
        CompletableFuture<Void> result = commandHandler.handle(command);

        // Then
        assertNotNull(result);
        assertEquals(expectedResult, result);
        verify(commandGateway).send(command);
    }

    @Test
    void handle_CreateUserCommand_WhenGatewayThrowsException_ShouldPropagateException() {
        // Given
        CreateUserCommand command = new CreateUserCommand(
                "user-123",
                "testuser",
                "test@example.com",
                "Test User"
        );
        RuntimeException expectedException = new RuntimeException("Command processing failed");
        when(commandGateway.send(command)).thenThrow(expectedException);

        // When & Then
        RuntimeException thrownException = assertThrows(RuntimeException.class, () -> {
            commandHandler.handle(command);
        });

        assertEquals(expectedException, thrownException);
        verify(commandGateway).send(command);
    }

    @Test
    void handle_UpdateUserCommand_WhenGatewayThrowsException_ShouldPropagateException() {
        // Given
        UpdateUserCommand command = new UpdateUserCommand(
                "user-123",
                "updateduser",
                "updated@example.com",
                "Updated User"
        );
        RuntimeException expectedException = new RuntimeException("Command processing failed");
        when(commandGateway.send(command)).thenThrow(expectedException);

        // When & Then
        RuntimeException thrownException = assertThrows(RuntimeException.class, () -> {
            commandHandler.handle(command);
        });

        assertEquals(expectedException, thrownException);
        verify(commandGateway).send(command);
    }
}