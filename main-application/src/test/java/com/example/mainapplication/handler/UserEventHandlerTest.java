package com.example.mainapplication.handler;

import com.example.mainapplication.event.UserCreatedEvent;
import com.example.mainapplication.event.UserUpdatedEvent;
import com.example.mainapplication.projection.UserProjection;
import com.example.mainapplication.repository.UserProjectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserEventHandlerTest {

    @Mock
    private UserProjectionRepository userProjectionRepository;

    @InjectMocks
    private UserEventHandler userEventHandler;

    private UserCreatedEvent userCreatedEvent;
    private UserUpdatedEvent userUpdatedEvent;
    private UserProjection existingProjection;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        
        userCreatedEvent = new UserCreatedEvent(
            "user-123",
            "johndoe",
            "john.doe@example.com",
            "John Doe",
            now
        );

        userUpdatedEvent = new UserUpdatedEvent(
            "user-123",
            "johndoe_updated",
            "john.doe.updated@example.com",
            "John Doe Updated",
            now.plusSeconds(3600)
        );

        existingProjection = new UserProjection();
        existingProjection.setId("user-123");
        existingProjection.setName("John Doe");
        existingProjection.setEmail("john.doe@example.com");
        existingProjection.setStatus("ACTIVE");
        existingProjection.setCreatedAt(OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
        existingProjection.setUpdatedAt(OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
    }

    @Test
    void shouldCreateUserProjectionOnUserCreatedEvent() {
        // Given
        when(userProjectionRepository.existsById("user-123")).thenReturn(false);
        when(userProjectionRepository.save(any(UserProjection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        userEventHandler.on(userCreatedEvent);

        // Then
        verify(userProjectionRepository).existsById("user-123");
        verify(userProjectionRepository).save(argThat(projection -> 
            projection.getId().equals("user-123") &&
            projection.getName().equals("John Doe") &&
            projection.getEmail().equals("john.doe@example.com") &&
            projection.getStatus().equals("ACTIVE")
        ));
    }

    @Test
    void shouldNotCreateDuplicateUserProjectionOnUserCreatedEvent() {
        // Given
        when(userProjectionRepository.existsById("user-123")).thenReturn(true);

        // When
        userEventHandler.on(userCreatedEvent);

        // Then
        verify(userProjectionRepository).existsById("user-123");
        verify(userProjectionRepository, never()).save(any(UserProjection.class));
    }

    @Test
    void shouldUpdateUserProjectionOnUserUpdatedEvent() {
        // Given
        when(userProjectionRepository.findById("user-123")).thenReturn(Optional.of(existingProjection));
        when(userProjectionRepository.save(any(UserProjection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        userEventHandler.on(userUpdatedEvent);

        // Then
        verify(userProjectionRepository).findById("user-123");
        verify(userProjectionRepository).save(argThat(projection -> 
            projection.getId().equals("user-123") &&
            projection.getName().equals("John Doe Updated") &&
            projection.getEmail().equals("john.doe.updated@example.com") &&
            projection.getStatus().equals("ACTIVE") // Status should remain unchanged
        ));
    }

    @Test
    void shouldThrowExceptionWhenUpdatingNonExistentUser() {
        // Given
        when(userProjectionRepository.findById("user-123")).thenReturn(Optional.empty());

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
            () -> userEventHandler.on(userUpdatedEvent));
        
        assertEquals("Cannot update non-existent user projection: user-123", exception.getMessage());
        verify(userProjectionRepository).findById("user-123");
        verify(userProjectionRepository, never()).save(any(UserProjection.class));
    }

    @Test
    void shouldPropagateExceptionOnUserCreatedEventProcessingError() {
        // Given
        when(userProjectionRepository.existsById("user-123")).thenReturn(false);
        when(userProjectionRepository.save(any(UserProjection.class)))
            .thenThrow(new RuntimeException("Database error"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> userEventHandler.on(userCreatedEvent));
        
        assertEquals("Database error", exception.getMessage());
    }

    @Test
    void shouldPropagateExceptionOnUserUpdatedEventProcessingError() {
        // Given
        when(userProjectionRepository.findById("user-123")).thenReturn(Optional.of(existingProjection));
        when(userProjectionRepository.save(any(UserProjection.class)))
            .thenThrow(new RuntimeException("Database error"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> userEventHandler.on(userUpdatedEvent));
        
        assertEquals("Database error", exception.getMessage());
    }
}