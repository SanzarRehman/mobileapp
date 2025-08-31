package com.example.mainapplication.service;

import com.example.mainapplication.LIB.service.ProjectionVersionService;
import com.example.mainapplication.LIB.service.ProjectionVersionService.ProjectionVersionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectionVersionServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ProjectionVersionService projectionVersionService;

    @BeforeEach
    void setUp() {
        projectionVersionService = new ProjectionVersionService(jdbcTemplate);
    }

    @Test
    void testGetCurrentVersion_ExistingProjection() {
        // Arrange
        String projectionName = "user-projections";
        when(jdbcTemplate.queryForList(anyString(), eq(Integer.class), eq(projectionName)))
            .thenReturn(List.of(2));

        // Act
        int version = projectionVersionService.getCurrentVersion(projectionName);

        // Assert
        assertEquals(2, version);
        verify(jdbcTemplate).queryForList(anyString(), eq(Integer.class), eq(projectionName));
    }

    @Test
    void testGetCurrentVersion_NonExistingProjection() {
        // Arrange
        String projectionName = "non-existent-projection";
        when(jdbcTemplate.queryForList(anyString(), eq(Integer.class), eq(projectionName)))
            .thenReturn(List.of());

        // Act
        int version = projectionVersionService.getCurrentVersion(projectionName);

        // Assert
        assertEquals(0, version);
    }

    @Test
    void testGetCurrentVersion_DatabaseException() {
        // Arrange
        String projectionName = "user-projections";
        when(jdbcTemplate.queryForList(anyString(), eq(Integer.class), eq(projectionName)))
            .thenThrow(new RuntimeException("Database error"));

        // Act
        int version = projectionVersionService.getCurrentVersion(projectionName);

        // Assert
        assertEquals(0, version); // Should return 0 on error
    }

    @Test
    void testUpdateVersion_Success() {
        // Arrange
        String projectionName = "user-projections";
        int version = 2;
        String description = "Added new fields";
        String migrationScript = "ALTER TABLE user_projection ADD COLUMN new_field VARCHAR(255)";

        // Act
        projectionVersionService.updateVersion(projectionName, version, description, migrationScript);

        // Assert
        verify(jdbcTemplate).update(anyString(), eq(projectionName), eq(version), eq(description), 
                                  eq(migrationScript), any(OffsetDateTime.class));
    }

    @Test
    void testGetVersionInfo_ExistingProjection() {
        // Arrange
        String projectionName = "user-projections";
        OffsetDateTime appliedAt = OffsetDateTime.now();
        Map<String, Object> row = Map.of(
            "projection_name", projectionName,
            "version", 2,
            "description", "Test version",
            "applied_at", appliedAt,
            "migration_script", "ALTER TABLE..."
        );

        when(jdbcTemplate.queryForList(anyString(), eq(projectionName)))
            .thenReturn(List.of(row));

        // Act
        Optional<ProjectionVersionInfo> info = projectionVersionService.getVersionInfo(projectionName);

        // Assert
        assertTrue(info.isPresent());
        assertEquals(projectionName, info.get().getProjectionName());
        assertEquals(2, info.get().getVersion());
        assertEquals("Test version", info.get().getDescription());
        assertEquals(appliedAt, info.get().getAppliedAt());
        assertEquals("ALTER TABLE...", info.get().getMigrationScript());
    }

    @Test
    void testGetVersionInfo_NonExistingProjection() {
        // Arrange
        String projectionName = "non-existent-projection";
        when(jdbcTemplate.queryForList(anyString(), eq(projectionName)))
            .thenReturn(List.of());

        // Act
        Optional<ProjectionVersionInfo> info = projectionVersionService.getVersionInfo(projectionName);

        // Assert
        assertFalse(info.isPresent());
    }

    @Test
    void testGetAllVersionInfo_Success() {
        // Arrange
        OffsetDateTime appliedAt = OffsetDateTime.now();
        ProjectionVersionInfo info1 = new ProjectionVersionInfo("proj1", 1, "desc1", appliedAt, null);
        ProjectionVersionInfo info2 = new ProjectionVersionInfo("proj2", 2, "desc2", appliedAt, "script");

        when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
            .thenReturn(List.of(info1, info2));

        // Act
        List<ProjectionVersionInfo> allInfo = projectionVersionService.getAllVersionInfo();

        // Assert
        assertEquals(2, allInfo.size());
        assertEquals("proj1", allInfo.get(0).getProjectionName());
        assertEquals("proj2", allInfo.get(1).getProjectionName());
    }

    @Test
    void testNeedsMigration_True() {
        // Arrange
        String projectionName = "user-projections";
        when(jdbcTemplate.queryForList(anyString(), eq(Integer.class), eq(projectionName)))
            .thenReturn(List.of(1)); // Current version is 1

        // Act
        boolean needsMigration = projectionVersionService.needsMigration(projectionName, 2);

        // Assert
        assertTrue(needsMigration);
    }

    @Test
    void testNeedsMigration_False() {
        // Arrange
        String projectionName = "user-projections";
        when(jdbcTemplate.queryForList(anyString(), eq(Integer.class), eq(projectionName)))
            .thenReturn(List.of(2)); // Current version is 2

        // Act
        boolean needsMigration = projectionVersionService.needsMigration(projectionName, 2);

        // Assert
        assertFalse(needsMigration);
    }

    @Test
    void testApplyMigration_Success() {
        // Arrange
        String projectionName = "user-projections";
        int fromVersion = 1;
        int toVersion = 2;
        String migrationScript = "ALTER TABLE user_projection ADD COLUMN new_field VARCHAR(255);";
        String description = "Added new field";

        when(jdbcTemplate.queryForList(anyString(), eq(Integer.class), eq(projectionName)))
            .thenReturn(List.of(fromVersion));

        // Act
        projectionVersionService.applyMigration(projectionName, fromVersion, toVersion, migrationScript, description);

        // Assert
        verify(jdbcTemplate).execute("ALTER TABLE user_projection ADD COLUMN new_field VARCHAR(255)");
        verify(jdbcTemplate).update(anyString(), eq(projectionName), eq(toVersion), eq(description), 
                                  eq(migrationScript), any(OffsetDateTime.class));
    }

    @Test
    void testApplyMigration_WrongCurrentVersion() {
        // Arrange
        String projectionName = "user-projections";
        int fromVersion = 1;
        int toVersion = 2;
        String migrationScript = "ALTER TABLE...";
        String description = "Migration";

        when(jdbcTemplate.queryForList(anyString(), eq(Integer.class), eq(projectionName)))
            .thenReturn(List.of(2)); // Current version is 2, not 1

        // Act & Assert
        assertThrows(ProjectionVersionService.ProjectionVersionException.class, () -> {
            projectionVersionService.applyMigration(projectionName, fromVersion, toVersion, migrationScript, description);
        });
    }

    @Test
    void testApplyMigration_EmptyMigrationScript() {
        // Arrange
        String projectionName = "user-projections";
        int fromVersion = 1;
        int toVersion = 2;
        String migrationScript = "";
        String description = "No script migration";

        when(jdbcTemplate.queryForList(anyString(), eq(Integer.class), eq(projectionName)))
            .thenReturn(List.of(fromVersion));

        // Act
        projectionVersionService.applyMigration(projectionName, fromVersion, toVersion, migrationScript, description);

        // Assert
        verify(jdbcTemplate, never()).execute(anyString()); // No script execution
        verify(jdbcTemplate).update(anyString(), eq(projectionName), eq(toVersion), eq(description), 
                                  eq(migrationScript), any(OffsetDateTime.class));
    }

    @Test
    void testInitializeProjectionVersion_NewProjection() {
        // Arrange
        String projectionName = "new-projection";
        int initialVersion = 1;
        String description = "Initial version";

        when(jdbcTemplate.queryForList(anyString(), eq(Integer.class), eq(projectionName)))
            .thenReturn(List.of()); // No existing version

        // Act
        projectionVersionService.initializeProjectionVersion(projectionName, initialVersion, description);

        // Assert
        verify(jdbcTemplate).update(anyString(), eq(projectionName), eq(initialVersion), eq(description), 
                                  isNull(), any(OffsetDateTime.class));
    }

    @Test
    void testInitializeProjectionVersion_ExistingProjection() {
        // Arrange
        String projectionName = "existing-projection";
        int initialVersion = 1;
        String description = "Initial version";

        when(jdbcTemplate.queryForList(anyString(), eq(Integer.class), eq(projectionName)))
            .thenReturn(List.of(2)); // Already has version 2

        // Act
        projectionVersionService.initializeProjectionVersion(projectionName, initialVersion, description);

        // Assert
        verify(jdbcTemplate, never()).update(anyString(), eq(projectionName), eq(initialVersion), 
                                           eq(description), isNull(), any(OffsetDateTime.class));
    }
}