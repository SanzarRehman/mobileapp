package com.example.mainapplication.config;

import com.example.mainapplication.projection.UserProjection;
import com.example.mainapplication.repository.UserProjectionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for database configuration and projection entity mappings.
 */
@DataJpaTest
@ActiveProfiles("test")
class DatabaseConfigTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private UserProjectionRepository userProjectionRepository;

    @Test
    void shouldConfigureDataSourceCorrectly() {
        assertThat(dataSource).isNotNull();
        assertThat(dataSource.getClass().getSimpleName()).contains("Hikari");
    }

    @Test
    void shouldPersistAndRetrieveUserProjection() {
        // Given
        UserProjection user = new UserProjection(
            "user-123",
            "John Doe",
            "john@example.com",
            "ACTIVE"
        );

        // When
        UserProjection savedUser = userProjectionRepository.save(user);

        // Then
        assertThat(savedUser.getId()).isEqualTo("user-123");
        assertThat(savedUser.getName()).isEqualTo("John Doe");
        assertThat(savedUser.getEmail()).isEqualTo("john@example.com");
        assertThat(savedUser.getStatus()).isEqualTo("ACTIVE");
        assertThat(savedUser.getCreatedAt()).isNotNull();
        assertThat(savedUser.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldFindUserByEmail() {
        // Given
        UserProjection user = new UserProjection(
            "user-456",
            "Jane Doe",
            "jane@example.com",
            "ACTIVE"
        );
        userProjectionRepository.save(user);

        // When
        Optional<UserProjection> foundUser = userProjectionRepository.findByEmail("jane@example.com");

        // Then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getName()).isEqualTo("Jane Doe");
    }

    @Test
    void shouldFindUsersByStatus() {
        // Given
        UserProjection user1 = new UserProjection("user-1", "User One", "user1@example.com", "ACTIVE");
        UserProjection user2 = new UserProjection("user-2", "User Two", "user2@example.com", "ACTIVE");
        UserProjection user3 = new UserProjection("user-3", "User Three", "user3@example.com", "INACTIVE");
        
        userProjectionRepository.save(user1);
        userProjectionRepository.save(user2);
        userProjectionRepository.save(user3);

        // When
        var activeUsers = userProjectionRepository.findByStatus("ACTIVE");

        // Then
        assertThat(activeUsers).hasSize(2);
        assertThat(activeUsers).extracting(UserProjection::getStatus).containsOnly("ACTIVE");
    }

    @Test
    void shouldUpdateTimestampOnUpdate() {
        // Given
        UserProjection user = new UserProjection(
            "user-789",
            "Test User",
            "test@example.com",
            "ACTIVE"
        );
        UserProjection savedUser = userProjectionRepository.save(user);
        var originalUpdatedAt = savedUser.getUpdatedAt();

        // When
        savedUser.setName("Updated Name");
        UserProjection updatedUser = userProjectionRepository.save(savedUser);

        // Then
        assertThat(updatedUser.getUpdatedAt()).isAfter(originalUpdatedAt);
        assertThat(updatedUser.getName()).isEqualTo("Updated Name");
    }
}