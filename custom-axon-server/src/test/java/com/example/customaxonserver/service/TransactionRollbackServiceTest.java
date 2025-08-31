package com.example.customaxonserver.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TransactionRollbackService.
 */
@ExtendWith(MockitoExtension.class)
class TransactionRollbackServiceTest {

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private TransactionStatus transactionStatus;

    private TransactionRollbackService transactionRollbackService;

    @BeforeEach
    void setUp() {
        transactionRollbackService = new TransactionRollbackService(transactionManager, transactionTemplate);
        transactionRollbackService.clearActiveTransactions();
    }

    @Test
    void testExecuteWithRollbackSuccess() {
        String transactionId = "test-tx";
        String expectedResult = "success";

        // Given: Transaction template executes successfully
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            return invocation.getArgument(0, org.springframework.transaction.support.TransactionCallback.class)
                    .doInTransaction(transactionStatus);
        });

        // When: Execute operation
        String result = transactionRollbackService.executeWithRollback(transactionId, () -> expectedResult);

        // Then: Should return expected result
        assertEquals(expectedResult, result);
        assertEquals(0, transactionRollbackService.getActiveTransactionCount());
    }

    @Test
    void testExecuteWithRollbackFailure() {
        String transactionId = "test-tx";
        RuntimeException testException = new RuntimeException("Test failure");

        // Given: Transaction template executes with failure
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            return invocation.getArgument(0, org.springframework.transaction.support.TransactionCallback.class)
                    .doInTransaction(transactionStatus);
        });

        // When: Execute operation that fails
        assertThrows(TransactionRollbackService.TransactionRollbackException.class, () -> {
            transactionRollbackService.executeWithRollback(transactionId, () -> {
                throw testException;
            });
        });

        // Then: Transaction should be cleaned up
        assertEquals(0, transactionRollbackService.getActiveTransactionCount());
    }

    @Test
    void testExecuteWithCompensationSuccess() {
        String sagaId = "test-saga";
        AtomicInteger executionCount = new AtomicInteger(0);
        
        List<TransactionRollbackService.CompensatableOperation<String>> operations = new ArrayList<>();
        
        // Add successful operations
        operations.add(new TransactionRollbackService.CompensatableOperation<String>() {
            @Override
            public String execute() {
                executionCount.incrementAndGet();
                return "op1";
            }

            @Override
            public TransactionRollbackService.CompensationAction getCompensationAction() {
                return () -> {}; // No compensation needed for successful saga
            }
        });

        operations.add(new TransactionRollbackService.CompensatableOperation<String>() {
            @Override
            public String execute() {
                executionCount.incrementAndGet();
                return "op2";
            }

            @Override
            public TransactionRollbackService.CompensationAction getCompensationAction() {
                return () -> {};
            }
        });

        // Given: Transaction template executes successfully
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            return invocation.getArgument(0, org.springframework.transaction.support.TransactionCallback.class)
                    .doInTransaction(transactionStatus);
        });

        // When: Execute saga
        CompletableFuture<String> future = transactionRollbackService.executeWithCompensation(sagaId, operations);
        String result = future.join();

        // Then: All operations should execute
        assertEquals("op2", result); // Last operation result
        assertEquals(2, executionCount.get());
    }

    @Test
    void testExecuteWithCompensationFailure() {
        String sagaId = "test-saga";
        AtomicInteger executionCount = new AtomicInteger(0);
        AtomicInteger compensationCount = new AtomicInteger(0);
        
        List<TransactionRollbackService.CompensatableOperation<String>> operations = new ArrayList<>();
        
        // First operation - succeeds
        operations.add(new TransactionRollbackService.CompensatableOperation<String>() {
            @Override
            public String execute() {
                executionCount.incrementAndGet();
                return "op1";
            }

            @Override
            public TransactionRollbackService.CompensationAction getCompensationAction() {
                return () -> compensationCount.incrementAndGet();
            }
        });

        // Second operation - fails
        operations.add(new TransactionRollbackService.CompensatableOperation<String>() {
            @Override
            public String execute() {
                executionCount.incrementAndGet();
                throw new RuntimeException("Operation failed");
            }

            @Override
            public TransactionRollbackService.CompensationAction getCompensationAction() {
                return () -> compensationCount.incrementAndGet();
            }
        });

        // Given: First transaction succeeds, second fails
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            return invocation.getArgument(0, org.springframework.transaction.support.TransactionCallback.class)
                    .doInTransaction(transactionStatus);
        });

        // When: Execute saga that fails
        CompletableFuture<String> future = transactionRollbackService.executeWithCompensation(sagaId, operations);
        
        assertThrows(TransactionRollbackService.SagaCompensationException.class, () -> {
            future.join();
        });

        // Then: Compensation should be executed for successful operations
        assertEquals(2, executionCount.get()); // Both operations attempted
        assertEquals(1, compensationCount.get()); // Only first operation compensated
    }

    @Test
    void testForceRollback() {
        String transactionId = "test-tx";

        // Given: Active transaction
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            // Simulate active transaction by calling force rollback from another thread
            new Thread(() -> {
                try {
                    Thread.sleep(100);
                    transactionRollbackService.forceRollback(transactionId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();

            return invocation.getArgument(0, org.springframework.transaction.support.TransactionCallback.class)
                    .doInTransaction(transactionStatus);
        });

        // When: Execute operation and force rollback
        assertThrows(TransactionRollbackService.TransactionRollbackException.class, () -> {
            transactionRollbackService.executeWithRollback(transactionId, () -> {
                try {
                    Thread.sleep(200); // Give time for force rollback
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "result";
            });
        });
    }

    @Test
    void testIsMarkedForRollback() {
        String transactionId = "test-tx";

        // Given: No active transaction
        assertFalse(transactionRollbackService.isMarkedForRollback(transactionId));

        // When: Transaction is active but not marked for rollback
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            // Check rollback status during transaction
            assertFalse(transactionRollbackService.isMarkedForRollback(transactionId));
            return "result";
        });

        transactionRollbackService.executeWithRollback(transactionId, () -> "result");
    }

    @Test
    void testGetTransactionStatus() {
        String transactionId = "test-tx";

        // Given: No active transaction
        assertNull(transactionRollbackService.getTransactionStatus(transactionId));

        // When: Transaction is active
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            // Check transaction status during transaction
            assertNotNull(transactionRollbackService.getTransactionStatus(transactionId));
            return "result";
        });

        transactionRollbackService.executeWithRollback(transactionId, () -> "result");
    }

    @Test
    void testActiveTransactionCount() {
        assertEquals(0, transactionRollbackService.getActiveTransactionCount());

        // Given: Transaction template that allows checking active count
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            // During transaction execution
            assertEquals(1, transactionRollbackService.getActiveTransactionCount());
            return "result";
        });

        // When: Execute transaction
        transactionRollbackService.executeWithRollback("test-tx", () -> "result");

        // Then: Count should be back to zero
        assertEquals(0, transactionRollbackService.getActiveTransactionCount());
    }

    @Test
    void testClearActiveTransactions() {
        // Given: Some active transactions (simulated)
        transactionRollbackService.clearActiveTransactions();
        
        // When: Clear active transactions
        transactionRollbackService.clearActiveTransactions();
        
        // Then: Count should be zero
        assertEquals(0, transactionRollbackService.getActiveTransactionCount());
    }

    @Test
    void testCompensationActionInterface() {
        AtomicInteger compensationExecuted = new AtomicInteger(0);
        
        TransactionRollbackService.CompensationAction action = () -> {
            compensationExecuted.incrementAndGet();
        };

        // When: Execute compensation action
        action.compensate();

        // Then: Action should be executed
        assertEquals(1, compensationExecuted.get());
    }

    @Test
    void testCompensatableOperationInterface() {
        String expectedResult = "operation-result";
        AtomicInteger compensationExecuted = new AtomicInteger(0);

        TransactionRollbackService.CompensatableOperation<String> operation = 
            new TransactionRollbackService.CompensatableOperation<String>() {
                @Override
                public String execute() {
                    return expectedResult;
                }

                @Override
                public TransactionRollbackService.CompensationAction getCompensationAction() {
                    return () -> compensationExecuted.incrementAndGet();
                }
            };

        // When: Execute operation
        String result = operation.execute();
        operation.getCompensationAction().compensate();

        // Then: Both operation and compensation should work
        assertEquals(expectedResult, result);
        assertEquals(1, compensationExecuted.get());
    }
}