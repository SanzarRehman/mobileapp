package com.example.customaxonserver.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class CircuitBreakerServiceTest {

    private CircuitBreakerService circuitBreakerService;

    @BeforeEach
    void setUp() {
        circuitBreakerService = new CircuitBreakerService();
    }

    @Test
    void executeWithCircuitBreaker_SuccessfulOperation_ShouldReturnResult() {
        // Given
        String serviceName = "test-service";
        String expectedResult = "success";

        // When
        String result = circuitBreakerService.executeWithCircuitBreaker(serviceName, () -> expectedResult);

        // Then
        assertEquals(expectedResult, result);
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreakerService.getCircuitBreakerState(serviceName));
    }

    @Test
    void executeWithCircuitBreaker_FailingOperation_ShouldPropagateException() {
        // Given
        String serviceName = "failing-service";
        RuntimeException expectedException = new RuntimeException("Test failure");

        // When & Then
        assertThrows(RuntimeException.class, () -> 
            circuitBreakerService.executeWithCircuitBreaker(serviceName, () -> {
                throw expectedException;
            })
        );
    }

    @Test
    void executeWithCircuitBreaker_MultipleFailures_ShouldOpenCircuit() {
        // Given
        String serviceName = "multiple-failures-service";
        AtomicInteger callCount = new AtomicInteger(0);

        // When - Execute multiple failing operations to trigger circuit breaker
        for (int i = 0; i < 10; i++) {
            try {
                circuitBreakerService.executeWithCircuitBreaker(serviceName, () -> {
                    callCount.incrementAndGet();
                    throw new RuntimeException("Simulated failure");
                });
            } catch (Exception e) {
                // Expected failures
            }
        }

        // Then - Circuit should eventually open
        CircuitBreaker.State state = circuitBreakerService.getCircuitBreakerState(serviceName);
        assertTrue(state == CircuitBreaker.State.OPEN || state == CircuitBreaker.State.HALF_OPEN);
        assertTrue(callCount.get() >= 5); // At least minimum number of calls were made
    }

    @Test
    void executeWithCircuitBreaker_RunnableOperation_ShouldExecuteSuccessfully() {
        // Given
        String serviceName = "runnable-service";
        AtomicInteger counter = new AtomicInteger(0);

        // When
        circuitBreakerService.executeWithCircuitBreaker(serviceName, counter::incrementAndGet);

        // Then
        assertEquals(1, counter.get());
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreakerService.getCircuitBreakerState(serviceName));
    }

    @Test
    void getCircuitBreakerState_NonExistentService_ShouldReturnNull() {
        // Given
        String nonExistentService = "non-existent-service";

        // When
        CircuitBreaker.State state = circuitBreakerService.getCircuitBreakerState(nonExistentService);

        // Then
        assertNull(state);
    }
}