package com.example.customaxonserver.resilience;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.kafka.KafkaException;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RetryServiceTest {

    private RetryService retryService;

    @BeforeEach
    void setUp() {
        retryService = new RetryService();
    }

    @Test
    void executeWithRetry_SuccessfulOperation_ShouldReturnResult() {
        // Given
        String operationName = "successful-operation";
        String expectedResult = "success";

        // When
        String result = retryService.executeWithRetry(operationName, () -> expectedResult);

        // Then
        assertEquals(expectedResult, result);
    }

    @Test
    void executeWithRetry_FailingThenSuccessfulOperation_ShouldRetryAndSucceed() {
        // Given
        String operationName = "retry-then-success";
        AtomicInteger attemptCount = new AtomicInteger(0);
        String expectedResult = "success";

        // When
        String result = retryService.executeWithRetry(operationName, () -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt < 3) {
                throw new DataAccessException("Simulated failure") {};
            }
            return expectedResult;
        });

        // Then
        assertEquals(expectedResult, result);
        assertEquals(3, attemptCount.get());
    }

    @Test
    void executeWithRetry_AlwaysFailingRetryableException_ShouldExhaustRetriesAndFail() {
        // Given
        String operationName = "always-failing-retryable";
        AtomicInteger attemptCount = new AtomicInteger(0);
        DataAccessException expectedException = new DataAccessException("Always failing") {};

        // When & Then
        DataAccessException thrownException = assertThrows(DataAccessException.class, () -> 
            retryService.executeWithRetry(operationName, () -> {
                attemptCount.incrementAndGet();
                throw expectedException;
            })
        );

        assertEquals(expectedException, thrownException);
        assertEquals(3, attemptCount.get()); // Should retry 3 times total
    }

    @Test
    void executeWithRetry_RedisConnectionFailure_ShouldRetry() {
        // Given
        String operationName = "redis-failure";
        AtomicInteger attemptCount = new AtomicInteger(0);
        String expectedResult = "success";

        // When
        String result = retryService.executeWithRetry(operationName, () -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt < 2) {
                throw new RedisConnectionFailureException("Redis failure");
            }
            return expectedResult;
        });

        // Then
        assertEquals(expectedResult, result);
        assertEquals(2, attemptCount.get());
    }

    @Test
    void executeWithRetry_KafkaException_ShouldRetry() {
        // Given
        String operationName = "kafka-failure";
        AtomicInteger attemptCount = new AtomicInteger(0);
        String expectedResult = "success";

        // When
        String result = retryService.executeWithRetry(operationName, () -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt < 2) {
                throw new KafkaException("Kafka failure");
            }
            return expectedResult;
        });

        // Then
        assertEquals(expectedResult, result);
        assertEquals(2, attemptCount.get());
    }

    @Test
    void executeWithRetry_TimeoutException_ShouldRetry() {
        // Given
        String operationName = "timeout-failure";
        AtomicInteger attemptCount = new AtomicInteger(0);
        String expectedResult = "success";

        // When
        String result = retryService.executeWithRetry(operationName, () -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt < 2) {
                try {
                    throw new TimeoutException("Timeout failure");
                } catch (TimeoutException e) {
                    throw new RuntimeException(e);
                }
            }
            return expectedResult;
        });

        // Then
        assertEquals(expectedResult, result);
        assertEquals(2, attemptCount.get());
    }

    @Test
    void executeWithRetry_NonRetryableException_ShouldFailImmediately() {
        // Given
        String operationName = "non-retryable-failure";
        AtomicInteger attemptCount = new AtomicInteger(0);
        IllegalArgumentException expectedException = new IllegalArgumentException("Non-retryable failure");

        // When & Then
        IllegalArgumentException thrownException = assertThrows(IllegalArgumentException.class, () -> 
            retryService.executeWithRetry(operationName, () -> {
                attemptCount.incrementAndGet();
                throw expectedException;
            })
        );

        assertEquals(expectedException, thrownException);
        assertEquals(1, attemptCount.get()); // Should not retry
    }
}