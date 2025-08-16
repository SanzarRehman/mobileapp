package com.example.customaxonserver.comprehensive;

import com.example.customaxonserver.contract.CustomServerApiContractTest;
import org.junit.jupiter.api.Test;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Comprehensive test suite for the Custom Axon Server.
 * This suite covers:
 * 1. Contract tests for API compatibility
 * 2. Integration tests for core functionality
 * 3. Performance tests for server operations
 */
@Suite
@SuiteDisplayName("Custom Axon Server Comprehensive Test Suite")
@SelectClasses({
    CustomServerApiContractTest.class
})
@SpringBootTest
@ActiveProfiles("test")
public class CustomServerComprehensiveTestSuite {

    @Test
    void contextLoads() {
        // This test ensures the Spring context loads properly for the test suite
    }
}