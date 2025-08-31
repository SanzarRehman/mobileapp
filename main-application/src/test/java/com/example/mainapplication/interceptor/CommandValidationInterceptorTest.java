package com.example.mainapplication.interceptor;

import com.example.mainapplication.command.CreateUserCommand;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CommandValidationInterceptor.
 */
@ExtendWith(MockitoExtension.class)
class CommandValidationInterceptorTest {

    @Mock
    private Validator validator;

    @Mock
    private ConstraintViolation<Object> violation;

    private CommandValidationInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new CommandValidationInterceptor(validator);
    }

    @Test
    void handle_ValidCommand_ShouldReturnOriginalCommand() {
        // Given
        CreateUserCommand command = new CreateUserCommand(
                "user-123",
                "testuser",
                "test@example.com",
                "Test User"
        );
        CommandMessage<?> commandMessage = GenericCommandMessage.asCommandMessage(command);
        List<? extends CommandMessage<?>> messages = Collections.singletonList(commandMessage);

        when(validator.validate(any())).thenReturn(Collections.emptySet());

        // When
        BiFunction<Integer, CommandMessage<?>, CommandMessage<?>> handler = interceptor.handle(messages);
        CommandMessage<?> result = handler.apply(0, commandMessage);

        // Then
        assertEquals(commandMessage, result);
        verify(validator).validate(command);
    }

    @Test
    void handle_InvalidCommand_ShouldThrowException() {
        // Given
        CreateUserCommand command = new CreateUserCommand(
                "user-123",
                "testuser",
                "test@example.com",
                "Test User"
        );
        CommandMessage<?> commandMessage = GenericCommandMessage.asCommandMessage(command);
        List<? extends CommandMessage<?>> messages = Collections.singletonList(commandMessage);

        Set<ConstraintViolation<Object>> violations = Collections.singleton(violation);
        when(validator.validate(any())).thenReturn(violations);
        when(violation.getPropertyPath()).thenReturn(mock(jakarta.validation.Path.class));
        when(violation.getPropertyPath().toString()).thenReturn("username");
        when(violation.getMessage()).thenReturn("must not be blank");

        // When & Then
        BiFunction<Integer, CommandMessage<?>, CommandMessage<?>> handler = interceptor.handle(messages);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            handler.apply(0, commandMessage);
        });

        assertTrue(exception.getMessage().contains("Command validation failed"));
        assertTrue(exception.getMessage().contains("username"));
        assertTrue(exception.getMessage().contains("must not be blank"));
        verify(validator).validate(command);
    }

    @Test
    void handle_MultipleViolations_ShouldIncludeAllInErrorMessage() {
        // Given
        CreateUserCommand command = new CreateUserCommand(
                "user-123",
                "testuser",
                "test@example.com",
                "Test User"
        );
        CommandMessage<?> commandMessage = GenericCommandMessage.asCommandMessage(command);
        List<? extends CommandMessage<?>> messages = Collections.singletonList(commandMessage);

        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation1 = mock(ConstraintViolation.class);
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation2 = mock(ConstraintViolation.class);
        
        when(violation1.getPropertyPath()).thenReturn(mock(jakarta.validation.Path.class));
        when(violation1.getPropertyPath().toString()).thenReturn("username");
        when(violation1.getMessage()).thenReturn("must not be blank");
        
        when(violation2.getPropertyPath()).thenReturn(mock(jakarta.validation.Path.class));
        when(violation2.getPropertyPath().toString()).thenReturn("email");
        when(violation2.getMessage()).thenReturn("must be valid");

        Set<ConstraintViolation<Object>> violations = Set.of(violation1, violation2);
        when(validator.validate(any())).thenReturn(violations);

        // When & Then
        BiFunction<Integer, CommandMessage<?>, CommandMessage<?>> handler = interceptor.handle(messages);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            handler.apply(0, commandMessage);
        });

        assertTrue(exception.getMessage().contains("Command validation failed"));
        assertTrue(exception.getMessage().contains("username"));
        assertTrue(exception.getMessage().contains("email"));
        verify(validator).validate(command);
    }
}