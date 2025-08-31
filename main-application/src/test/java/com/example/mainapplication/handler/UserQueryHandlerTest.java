package com.example.mainapplication.handler;

import com.example.mainapplication.projection.UserProjection;
import com.example.mainapplication.query.FindAllUsersQuery;
import com.example.mainapplication.query.FindUserByEmailQuery;
import com.example.mainapplication.query.FindUserByIdQuery;
import com.example.mainapplication.repository.UserProjectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserQueryHandlerTest {

    @Mock
    private UserProjectionRepository userProjectionRepository;

    @InjectMocks
    private UserQueryHandler userQueryHandler;

    private UserProjection userProjection1;
    private UserProjection userProjection2;
    private UserProjection userProjection3;

    @BeforeEach
    void setUp() {
        OffsetDateTime now = OffsetDateTime.now();
        
        userProjection1 = new UserProjection();
        userProjection1.setId("user-1");
        userProjection1.setName("John Doe");
        userProjection1.setEmail("john.doe@example.com");
        userProjection1.setStatus("ACTIVE");
        userProjection1.setCreatedAt(now.minusDays(2));
        userProjection1.setUpdatedAt(now.minusDays(1));

        userProjection2 = new UserProjection();
        userProjection2.setId("user-2");
        userProjection2.setName("Jane Smith");
        userProjection2.setEmail("jane.smith@example.com");
        userProjection2.setStatus("INACTIVE");
        userProjection2.setCreatedAt(now.minusDays(1));
        userProjection2.setUpdatedAt(now);

        userProjection3 = new UserProjection();
        userProjection3.setId("user-3");
        userProjection3.setName("Bob Johnson");
        userProjection3.setEmail("bob.johnson@example.com");
        userProjection3.setStatus("ACTIVE");
        userProjection3.setCreatedAt(now);
        userProjection3.setUpdatedAt(now);
    }

    @Test
    void shouldFindUserByIdWhenExists() {
        // Given
        FindUserByIdQuery query = new FindUserByIdQuery("user-1");
        when(userProjectionRepository.findById("user-1")).thenReturn(Optional.of(userProjection1));

        // When
        Optional<UserProjection> result = userQueryHandler.handle(query);

        // Then
        assertTrue(result.isPresent());
        assertEquals(userProjection1, result.get());
        verify(userProjectionRepository).findById("user-1");
    }

    @Test
    void shouldReturnEmptyWhenUserByIdNotFound() {
        // Given
        FindUserByIdQuery query = new FindUserByIdQuery("non-existent");
        when(userProjectionRepository.findById("non-existent")).thenReturn(Optional.empty());

        // When
        Optional<UserProjection> result = userQueryHandler.handle(query);

        // Then
        assertFalse(result.isPresent());
        verify(userProjectionRepository).findById("non-existent");
    }

    @Test
    void shouldFindUserByEmailWhenExists() {
        // Given
        FindUserByEmailQuery query = new FindUserByEmailQuery("jane.smith@example.com");
        when(userProjectionRepository.findByEmail("jane.smith@example.com")).thenReturn(Optional.of(userProjection2));

        // When
        Optional<UserProjection> result = userQueryHandler.handle(query);

        // Then
        assertTrue(result.isPresent());
        assertEquals(userProjection2, result.get());
        verify(userProjectionRepository).findByEmail("jane.smith@example.com");
    }

    @Test
    void shouldReturnEmptyWhenUserByEmailNotFound() {
        // Given
        FindUserByEmailQuery query = new FindUserByEmailQuery("nonexistent@example.com");
        when(userProjectionRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        // When
        Optional<UserProjection> result = userQueryHandler.handle(query);

        // Then
        assertFalse(result.isPresent());
        verify(userProjectionRepository).findByEmail("nonexistent@example.com");
    }

    @Test
    void shouldFindAllUsersWithoutFiltering() {
        // Given
        FindAllUsersQuery query = new FindAllUsersQuery();
        List<UserProjection> allUsers = Arrays.asList(userProjection3, userProjection2, userProjection1);
        when(userProjectionRepository.findAllOrderByCreatedAtDesc()).thenReturn(allUsers);

        // When
        List<UserProjection> result = userQueryHandler.handle(query);

        // Then
        assertEquals(3, result.size());
        assertEquals(allUsers, result);
        verify(userProjectionRepository).findAllOrderByCreatedAtDesc();
    }

    @Test
    void shouldFindUsersByStatus() {
        // Given
        FindAllUsersQuery query = new FindAllUsersQuery("ACTIVE", null, 0, 20);
        List<UserProjection> activeUsers = Arrays.asList(userProjection1, userProjection3);
        when(userProjectionRepository.findByStatus("ACTIVE")).thenReturn(activeUsers);

        // When
        List<UserProjection> result = userQueryHandler.handle(query);

        // Then
        assertEquals(2, result.size());
        assertEquals(activeUsers, result);
        verify(userProjectionRepository).findByStatus("ACTIVE");
    }

    @Test
    void shouldSearchUsersByNameOrEmail() {
        // Given
        FindAllUsersQuery query = new FindAllUsersQuery(null, "john", 0, 20);
        List<UserProjection> searchResults = Arrays.asList(userProjection1, userProjection3);
        when(userProjectionRepository.searchByNameOrEmail("john")).thenReturn(searchResults);

        // When
        List<UserProjection> result = userQueryHandler.handle(query);

        // Then
        assertEquals(2, result.size());
        assertEquals(searchResults, result);
        verify(userProjectionRepository).searchByNameOrEmail("john");
    }

    @Test
    void shouldApplyPaginationCorrectly() {
        // Given
        FindAllUsersQuery query = new FindAllUsersQuery(null, null, 1, 2); // Page 1, size 2
        List<UserProjection> allUsers = Arrays.asList(userProjection1, userProjection2, userProjection3);
        when(userProjectionRepository.findAllOrderByCreatedAtDesc()).thenReturn(allUsers);

        // When
        List<UserProjection> result = userQueryHandler.handle(query);

        // Then
        assertEquals(1, result.size()); // Should return only 1 item (3rd item from page 1 with size 2)
        assertEquals(userProjection3, result.get(0));
        verify(userProjectionRepository).findAllOrderByCreatedAtDesc();
    }

    @Test
    void shouldReturnEmptyListWhenPageBeyondAvailableData() {
        // Given
        FindAllUsersQuery query = new FindAllUsersQuery(null, null, 5, 10); // Page 5, size 10
        List<UserProjection> allUsers = Arrays.asList(userProjection1, userProjection2);
        when(userProjectionRepository.findAllOrderByCreatedAtDesc()).thenReturn(allUsers);

        // When
        List<UserProjection> result = userQueryHandler.handle(query);

        // Then
        assertTrue(result.isEmpty());
        verify(userProjectionRepository).findAllOrderByCreatedAtDesc();
    }

    @Test
    void shouldPropagateExceptionOnRepositoryError() {
        // Given
        FindUserByIdQuery query = new FindUserByIdQuery("user-1");
        when(userProjectionRepository.findById("user-1"))
            .thenThrow(new RuntimeException("Database error"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> userQueryHandler.handle(query));
        
        assertEquals("Database error", exception.getMessage());
    }
}