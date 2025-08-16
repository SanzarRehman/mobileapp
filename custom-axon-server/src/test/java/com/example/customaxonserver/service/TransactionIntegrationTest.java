package com.example.customaxonserver.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration tests for transaction consistency features.
 * Tests the interaction between ConcurrencyControlService and TransactionRollbackService.
 */
@ExtendWith(MockitoExtension.class)
class TransactionIntegrationTest {

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionTemplate transactionTemplate;

    private ConcurrencyControlService concurrencyControlService;
    private TransactionRollbackService transactionRollbackService;

    @BeforeEach
    void setUp() {
        concurrencyControlService = new ConcurrencyControlService();
        transactionRollbackService = new TransactionRollbackService(transactionManager, transactionTemplate);
    }

    @Test
    void testConcurrentOperationsWithTransactionControl() throws InterruptedException {
        String aggregateId = "test-aggregate";
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // Mock transaction template to execute operations
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            return invocation.getArgument(0, org.springframework.transaction.support.TransactionCallback.class)
                    .doInTransaction(null);
        });

        // When: Multiple threads perform operations with both concurrency and transaction control
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    String transactionId = "tx-" + threadId;
                    
                    // Use both concurrency control and transaction rollback
                    String result = concurrencyControlService.executeWithWriteLock(aggregateId, () -> {
                        return transactionRollbackService.executeWithRollback(transactionId, () -> {
                            // Simulate some work
                            return "result-" + threadId;
                        });
                    });
                    
                    assertNotNull(result);
                    successCount.incrementAndGet();
                    
                } catch (Exception e) {
                    // Some operations might fail due to concurrency
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Then: All operations should succeed (they're using different transaction IDs)
        assertEquals(threadCount, successCount.get());
    }

    @Test
    void testSagaWithConcurrencyControl() {
        String sagaId = "test-saga";
        String aggregateId = "test-aggregate";
        AtomicInteger compensationCount = new AtomicInteger(0);

        List<TransactionRollbackService.CompensatableOperation<String>> operations = new ArrayList<>();
        
        // First operation - succeeds with concurrency control
        operations.add(new TransactionRollbackService.CompensatableOperation<String>() {
            @Override
            public String execute() {
                return concurrencyControlService.executeWithWriteLock(aggregateId, () -> {
                    return "operation1-result";
                });
            }

            @Override
            public TransactionRollbackService.CompensationAction getCompensationAction() {
                return () -> compensationCount.incrementAndGet();
            }
        });

        // Second operation - also succeeds
        operations.add(new TransactionRollbackService.CompensatableOperation<String>() {
            @Override
            public String execute() {
                return concurrencyControlService.executeWithWriteLock(aggregateId, () -> {
                    return "operation2-result";
                });
            }

            @Override
            public TransactionRollbackService.CompensationAction getCompensationAction() {
                return () -> compensationCount.incrementAndGet();
            }
        });

        // Mock transaction template to execute operations
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            return invocation.getArgument(0, org.springframework.transaction.support.TransactionCallback.class)
                    .doInTransaction(null);
        });

        // When: Execute saga with concurrency control
        CompletableFuture<String> future = transactionRollbackService.executeWithCompensation(sagaId, operations);
        String result = future.join();

        // Then: Saga should complete successfully
        assertEquals("operation2-result", result);
        assertEquals(0, compensationCount.get()); // No compensation needed
    }

    @Test
    void testOptimisticLockingWithTransactionRollback() {
        String aggregateId = "test-aggregate";
        String transactionId = "test-tx";

        // Mock transaction template to execute operations
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            return invocation.getArgument(0, org.springframework.transaction.support.TransactionCallback.class)
                    .doInTransaction(null);
        });

        // When: Execute operation with both optimistic locking and transaction control
        String result = transactionRollbackService.executeWithRollback(transactionId, () -> {
            return concurrencyControlService.executeWithOptimisticLocking(aggregateId, () -> {
                // Simulate successful operation
                return "success";
            });
        });

        // Then: Operation should complete successfully
        assertEquals("success", result);
    }

    @Test
    void testVersionValidationIntegration() {
        String aggregateId = "test-aggregate";
        Long expectedVersion = 5L;
        Long actualVersion = 5L;

        // When: Validate version within concurrency control
        assertDoesNotThrow(() -> {
            concurrencyControlService.executeWithReadLock(aggregateId, () -> {
                concurrencyControlService.validateVersion(aggregateId, expectedVersion, actualVersion);
                return null;
            });
        });

        // When: Version mismatch
        Long wrongVersion = 6L;
        assertThrows(org.springframework.dao.OptimisticLockingFailureException.class, () -> {
            concurrencyControlService.executeWithReadLock(aggregateId, () -> {
                concurrencyControlService.validateVersion(aggregateId, expectedVersion, wrongVersion);
                return null;
            });
        });
    }

    @Test
    void testLockManagement() {
        String aggregateId1 = "aggregate-1";
        String aggregateId2 = "aggregate-2";

        // Initially no locks
        assertEquals(0, concurrencyControlService.getManagedLockCount());

        // Create locks by executing operations
        concurrencyControlService.executeWithWriteLock(aggregateId1, () -> {
            concurrencyControlService.executeWithWriteLock(aggregateId2, () -> null);
            
            // During execution, locks should be tracked
            assertEquals(2, concurrencyControlService.getManagedLockCount());
            return null;
        });

        // After execution, locks should still be tracked (they're not automatically cleared)
        assertEquals(2, concurrencyControlService.getManagedLockCount());

        // Clear locks
        concurrencyControlService.clearLock(aggregateId1);
        concurrencyControlService.clearLock(aggregateId2);
        assertEquals(0, concurrencyControlService.getManagedLockCount());
    }

    @Test
    void testTransactionStateTracking() {
        String transactionId = "test-tx";

        // Mock transaction template to execute operations
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            return invocation.getArgument(0, org.springframework.transaction.support.TransactionCallback.class)
                    .doInTransaction(null);
        });

        // Initially no active transactions
        assertEquals(0, transactionRollbackService.getActiveTransactionCount());

        // Execute transaction
        transactionRollbackService.executeWithRollback(transactionId, () -> {
            // During execution, transaction should be tracked
            assertEquals(1, transactionRollbackService.getActiveTransactionCount());
            return "result";
        });

        // After execution, transaction should be cleaned up
        assertEquals(0, transactionRollbackService.getActiveTransactionCount());
    }
}