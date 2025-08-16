package com.example.mainapplication.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing projection versions and migrations.
 * Provides functionality to track projection schema versions and handle migrations.
 */
@Service
public class ProjectionVersionService {

    private static final Logger logger = LoggerFactory.getLogger(ProjectionVersionService.class);

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public ProjectionVersionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        initializeVersionTable();
    }

    /**
     * Initializes the projection version tracking table if it doesn't exist.
     */
    @Transactional
    private void initializeVersionTable() {
        try {
            String createTableSql = """
                CREATE TABLE IF NOT EXISTS projection_versions (
                    projection_name VARCHAR(255) PRIMARY KEY,
                    version INTEGER NOT NULL,
                    description VARCHAR(500),
                    applied_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                    migration_script VARCHAR(1000)
                )
                """;
            
            jdbcTemplate.execute(createTableSql);
            
            // Create index for performance
            String createIndexSql = """
                CREATE INDEX IF NOT EXISTS idx_projection_versions_applied_at 
                ON projection_versions(applied_at)
                """;
            
            jdbcTemplate.execute(createIndexSql);
            
            logger.info("Projection version tracking table initialized");
            
        } catch (Exception e) {
            logger.error("Failed to initialize projection version table", e);
            throw new ProjectionVersionException("Failed to initialize version tracking", e);
        }
    }

    /**
     * Gets the current version of a projection.
     *
     * @param projectionName The name of the projection
     * @return Current version or 0 if not found
     */
    public int getCurrentVersion(String projectionName) {
        try {
            String sql = "SELECT version FROM projection_versions WHERE projection_name = ?";
            List<Integer> versions = jdbcTemplate.queryForList(sql, Integer.class, projectionName);
            
            return versions.isEmpty() ? 0 : versions.get(0);
            
        } catch (Exception e) {
            logger.error("Failed to get current version for projection: {}", projectionName, e);
            return 0;
        }
    }

    /**
     * Updates the version of a projection.
     *
     * @param projectionName The name of the projection
     * @param version The new version number
     * @param description Description of the version change
     * @param migrationScript Optional migration script that was applied
     */
    @Transactional
    public void updateVersion(String projectionName, int version, String description, String migrationScript) {
        try {
            String sql = """
                INSERT INTO projection_versions (projection_name, version, description, migration_script, applied_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (projection_name) 
                DO UPDATE SET 
                    version = EXCLUDED.version,
                    description = EXCLUDED.description,
                    migration_script = EXCLUDED.migration_script,
                    applied_at = EXCLUDED.applied_at
                """;
            
            jdbcTemplate.update(sql, projectionName, version, description, migrationScript, OffsetDateTime.now());
            
            logger.info("Updated projection {} to version {} - {}", projectionName, version, description);
            
        } catch (Exception e) {
            logger.error("Failed to update version for projection: {}", projectionName, e);
            throw new ProjectionVersionException("Failed to update projection version", e);
        }
    }

    /**
     * Gets version information for a projection.
     *
     * @param projectionName The name of the projection
     * @return Version information or empty if not found
     */
    public Optional<ProjectionVersionInfo> getVersionInfo(String projectionName) {
        try {
            String sql = """
                SELECT projection_name, version, description, applied_at, migration_script
                FROM projection_versions 
                WHERE projection_name = ?
                """;
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, projectionName);
            
            if (results.isEmpty()) {
                return Optional.empty();
            }
            
            Map<String, Object> row = results.get(0);
            ProjectionVersionInfo info = new ProjectionVersionInfo(
                (String) row.get("projection_name"),
                (Integer) row.get("version"),
                (String) row.get("description"),
                (OffsetDateTime) row.get("applied_at"),
                (String) row.get("migration_script")
            );
            
            return Optional.of(info);
            
        } catch (Exception e) {
            logger.error("Failed to get version info for projection: {}", projectionName, e);
            return Optional.empty();
        }
    }

    /**
     * Gets version information for all projections.
     *
     * @return List of all projection version information
     */
    public List<ProjectionVersionInfo> getAllVersionInfo() {
        try {
            String sql = """
                SELECT projection_name, version, description, applied_at, migration_script
                FROM projection_versions 
                ORDER BY applied_at DESC
                """;
            
            return jdbcTemplate.query(sql, (rs, rowNum) -> new ProjectionVersionInfo(
                rs.getString("projection_name"),
                rs.getInt("version"),
                rs.getString("description"),
                rs.getObject("applied_at", OffsetDateTime.class),
                rs.getString("migration_script")
            ));
            
        } catch (Exception e) {
            logger.error("Failed to get all version info", e);
            throw new ProjectionVersionException("Failed to retrieve version information", e);
        }
    }

    /**
     * Checks if a projection needs migration based on expected version.
     *
     * @param projectionName The name of the projection
     * @param expectedVersion The expected version
     * @return true if migration is needed
     */
    public boolean needsMigration(String projectionName, int expectedVersion) {
        int currentVersion = getCurrentVersion(projectionName);
        return currentVersion < expectedVersion;
    }

    /**
     * Applies a migration script for a projection.
     *
     * @param projectionName The name of the projection
     * @param fromVersion The version to migrate from
     * @param toVersion The version to migrate to
     * @param migrationScript The SQL migration script
     * @param description Description of the migration
     */
    @Transactional
    public void applyMigration(String projectionName, int fromVersion, int toVersion, 
                             String migrationScript, String description) {
        try {
            int currentVersion = getCurrentVersion(projectionName);
            if (currentVersion != fromVersion) {
                throw new ProjectionVersionException(
                    String.format("Cannot migrate projection %s from version %d to %d - current version is %d",
                                projectionName, fromVersion, toVersion, currentVersion));
            }
            
            logger.info("Applying migration for projection {} from version {} to {}", 
                       projectionName, fromVersion, toVersion);
            
            // Execute migration script
            if (migrationScript != null && !migrationScript.trim().isEmpty()) {
                String[] statements = migrationScript.split(";");
                for (String statement : statements) {
                    if (!statement.trim().isEmpty()) {
                        jdbcTemplate.execute(statement.trim());
                    }
                }
            }
            
            // Update version
            updateVersion(projectionName, toVersion, description, migrationScript);
            
            logger.info("Successfully migrated projection {} from version {} to {}", 
                       projectionName, fromVersion, toVersion);
            
        } catch (Exception e) {
            logger.error("Failed to apply migration for projection: {}", projectionName, e);
            throw new ProjectionVersionException("Migration failed for projection " + projectionName, e);
        }
    }

    /**
     * Initializes a projection version if it doesn't exist.
     *
     * @param projectionName The name of the projection
     * @param initialVersion The initial version number
     * @param description Description of the initial version
     */
    @Transactional
    public void initializeProjectionVersion(String projectionName, int initialVersion, String description) {
        if (getCurrentVersion(projectionName) == 0) {
            updateVersion(projectionName, initialVersion, description, null);
            logger.info("Initialized projection {} with version {}", projectionName, initialVersion);
        }
    }

    /**
     * Data class containing projection version information.
     */
    public static class ProjectionVersionInfo {
        private final String projectionName;
        private final int version;
        private final String description;
        private final OffsetDateTime appliedAt;
        private final String migrationScript;

        public ProjectionVersionInfo(String projectionName, int version, String description, 
                                   OffsetDateTime appliedAt, String migrationScript) {
            this.projectionName = projectionName;
            this.version = version;
            this.description = description;
            this.appliedAt = appliedAt;
            this.migrationScript = migrationScript;
        }

        // Getters
        public String getProjectionName() { return projectionName; }
        public int getVersion() { return version; }
        public String getDescription() { return description; }
        public OffsetDateTime getAppliedAt() { return appliedAt; }
        public String getMigrationScript() { return migrationScript; }
    }

    /**
     * Exception thrown when projection version operations fail.
     */
    public static class ProjectionVersionException extends RuntimeException {
        public ProjectionVersionException(String message) {
            super(message);
        }

        public ProjectionVersionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}