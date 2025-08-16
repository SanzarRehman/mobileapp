package com.example.customaxonserver.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConcurrencyControlService.
 */
@ExtendWith(MockitoExtension.class)
class ConcurrencyControlServiceTest {

    private ConcurrencyControlService concurrencyControlService;

    @BeforeEach
    void setUp() {
        concurrencyControlService = new ConcurrencyControlService();
    }

    @Test
    void testExecuteWithReadLock() {
        String aggregateId = "test-aggregate";
        String result = "test-result";

        // When: Execute operation with read lock
        String actualResult = concurrencyControlService.executeWithReadLock(aggregateId, () -> result);

        // Then: Operation should complete successfully
        assertEquals(result, actualResult);
        assertEquals(0, concurrencyControlService.getReadLockCount(aggregateId));
    }

    @Test
    void testExecuteWithWriteLock() {
        String aggregateId = "test-aggregate";
        String result = "test-result";

        // When: Execute operation with write lock
        String actualResult = concurrencyControlService.executeWithWriteLock(aggregateId, () -> result);

        // Then: Operation should complete successfully
        assertEquals(result, actualResult);
        assertFalse(concurrencyControlService.isWriteLocked(aggregateId));
    }

    @Test
    void testConcurrentReadOperations() throws InterruptedException {
        String aggregateId = "test-aggregate";
        int readerCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(readerCount);
        CountDownLatch latch = new CountDownLatch(readerCount);
        AtomicInteger completedReads = new AtomicInteger(0);

        // When: Multiple threads perform read operations
        for (int i = 0; i < readerCount; i++) {
            executor.submit(() -> {
                try {
                    concurrencyControlService.executeWithReadLock(aggregateId, () -> {
                        try {
                            Thread.sleep(100); // Simulate read operation
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        completedReads.incrementAndGet();
                        return null;
                    });
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Then: All read operations should complete
        assertEquals(readerCount, completedReads.get());
    }

    @Test
    void testWriteLockExclusivity() throws InterruptedException {
        String aggregateId = "test-aggregate";
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(2);
        AtomicInteger operationOrder = new AtomicInteger(0);

        // Start first write operation
        Thread writer1 = new Thread(() -> {
            try {
                startLatch.await();
                concurrencyControlService.executeWithWriteLock(aggregateId, () -> {
                    operationOrder.compareAndSet(0, 1);
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                completeLatch.countDown();
            }
        });

        // Start second write operation
        Thread writer2 = new Thread(() -> {
            try {
                startLatch.await();
                Thread.sleep(50); // Start slightly after first writer
                concurrencyControlService.executeWithWriteLock(aggregateId, () -> {
                    operationOrder.compareAndSet(1, 2);
                    return null;
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                completeLatch.countDown();
            }
        });

        writer1.start();
        writer2.start();
        startLatch.countDown();
        completeLatch.await();

        // Then: Operations should execute sequentially
        assertEquals(2, operationOrder.get());
    }

    @Test
    void testValidateVersionSuccess() {
        String aggregateId = "test-aggregate";
        Long expectedVersion = 5L;
        Long actualVersion = 5L;

        // When: Versions match
        assertDoesNotThrow(() -> {
            concurrencyControlService.validateVersion(aggregateId, expectedVersion, actualVersion);
        });
    }

    @Test
    void testValidateVersionFailure() {
        String aggregateId = "test-aggregate";
        Long expectedVersion = 5L;
        Long actualVersion = 6L;

        // When: Versions don't match
        assertThrows(OptimisticLockingFailureException.class, () -> {
            concurrencyControlService.validateVersion(aggregateId, expectedVersion, actualVersion);
        });
    }

    @Test
    void testValidateVersionWithNullExpected() {
        String aggregateId = "test-aggregate";
        Long expectedVersion = null;
        Long actualVersion = 5L;

        // When: Expected version is null (no validation)
        assertDoesNotThrow(() -> {
            concurrencyControlService.validateVersion(aggregateId, expectedVersion, actualVersion);
        });
    }

    @Test
    void testClearLock() {
        String aggregateId = "test-aggregate";

        // Given: Execute operation to create lock
        concurrencyControlService.executeWithWriteLock(aggregateId, () -> null);
        assertTrue(concurrencyControlService.getManagedLockCount() > 0);

        // When: Clear lock
        concurrencyControlService.clearLock(aggregateId);

        // Then: Lock should be removed
        assertEquals(0, concurrencyControlService.getManagedLockCount());
    }

    @Test
    void testLockStatistics() {
        String aggregateId1 = "test-aggregate-1";
        String aggregateId2 = "test-aggregate-2";

        // When: Create locks for different aggregates
        concurrencyControlService.executeWithWriteLock(aggregateId1, () -> {
            concurrencyControlService.executeWithWriteLock(aggregateId2, () -> null);
            
            // Then: Should track multiple locks
            assertEquals(2, concurrencyControlService.getManagedLockCount());
            return null;
        });

        // After operations complete, locks should still be tracked
        assertEquals(2, concurrencyControlService.getManagedLockCount());
    }

    @Test
    void testOptimisticLockingRetry() {
        String aggregateId = "test-aggregate";
        AtomicInteger attemptCount = new AtomicInteger(0);

        // When: Operation succeeds without retry (since @Retryable doesn't work in unit tests)
        String result = concurrencyControlService.executeWithOptimisticLocking(aggregateId, () -> {
            attemptCount.incrementAndGet();
            return "success";
        });

        // Then: Should succeed
        assertEquals("success", result);
        assertEquals(1, attemptCount.get());
    }

    @Test
    void testFullConcurrencyControl() {
        String aggregateId = "test-aggregate";
        AtomicInteger executionCount = new AtomicInteger(0);

        // When: Execute with full concurrency control
        String result = concurrencyControlService.executeWithFullConcurrencyControl(aggregateId, () -> {
            executionCount.incrementAndGet();
            return "controlled-result";
        });

        // Then: Operation should complete successfully
        assertEquals("controlled-result", result);
        assertEquals(1, executionCount.get());
    }
}