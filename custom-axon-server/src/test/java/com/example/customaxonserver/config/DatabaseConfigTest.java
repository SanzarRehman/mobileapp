package com.example.customaxonserver.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for database configuration.
 * Tests basic database connectivity and configuration.
 */
@SpringBootTest
@ActiveProfiles("test")
class DatabaseConfigTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void shouldConfigureDataSourceCorrectly() {
        assertThat(dataSource).isNotNull();
        // In test profile, Spring Boot uses embedded datasource
        assertThat(dataSource.getClass().getSimpleName()).contains("DataSource");
    }

    @Test
    void shouldConnectToDatabase() throws Exception {
        // Test basic database connectivity
        try (var connection = dataSource.getConnection()) {
            assertThat(connection).isNotNull();
            assertThat(connection.isValid(5)).isTrue();
        }
    }
}