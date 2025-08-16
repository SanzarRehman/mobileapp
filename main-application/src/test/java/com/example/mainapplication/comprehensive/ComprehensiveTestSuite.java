package com.example.mainapplication.comprehensive;

import com.example.mainapplication.chaos.ChaosEngineeringTest;
import com.example.mainapplication.contract.ApiContractTest;
import com.example.mainapplication.integration.EndToEndIntegrationTest;
import com.example.mainapplication.load.ScalabilityLoadTest;
import com.example.mainapplication.performance.HighThroughputPerformanceTest;
import org.junit.jupiter.api.Test;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Comprehensive test suite that runs all integration tests in sequence.
 * This suite covers:
 * 1. End-to-end integration tests
 * 2. Performance tests for high-throughput scenarios
 * 3. Contract tests for API compatibility
 * 4. Chaos engineering tests for failure scenarios
 * 5. Load tests for scalability validation
 */
@Suite
@SuiteDisplayName("Comprehensive Integration Test Suite")
@SelectClasses({
    EndToEndIntegrationTest.class,
    HighThroughputPerformanceTest.class,
    ApiContractTest.class,
    ChaosEngineeringTest.class,
    ScalabilityLoadTest.class
})
@SpringBootTest
@ActiveProfiles("test")
public class ComprehensiveTestSuite {

    @Test
    void contextLoads() {
        // This test ensures the Spring context loads properly for the test suite
    }
}