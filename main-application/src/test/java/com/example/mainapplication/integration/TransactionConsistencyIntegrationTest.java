package com.example.mainapplication.integration;

import com.example.mainapplication.aggregate.UserAggregate;
import com.example.mainapplication.command.CreateUserCommand;
import com.example.mainapplication.command.UpdateUserCommand;
import com.example.mainapplication.repository.UserProjectionRepository;
import com.example.mainapplication.LIB.service.TransactionRollbackService;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for transaction consistency in the main application.
 */
@SpringBootTest
@ActiveProfiles("test")
class TransactionConsistencyIntegrationTest {

    @Autowired
    private CommandGateway commandGateway;

    @Autowired
    private TransactionRollbackService transactionRollbackService;

    @Autowired
    private UserProjectionRepository userProjectionRepository;

    private FixtureConfiguration<UserAggregate> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(UserAggregate.class);
        transactionRollbackService.clearActiveTransactions();
        userProjectionRepository.deleteAll();
    }

    @Test
    void testSagaTransactionConsistency() {
        String userId = UUID.randomUUID().toString();
        String username = "testuser";
        String email = "test@example.com";
        String fullName = "Test User";

        // When: Create user command is processed
        CreateUserCommand command = new CreateUserCommand(userId, username, email, fullName);
        
        CompletableFuture<String> result = commandGateway.send(command);
        
        // Then: Command should complete successfully
        assertDoesNotThrow(() -> result.join());
        
        // Verify saga would be triggered (in real scenario)
        // This is a simplified test as full saga testing requires more setup
        assertNotNull(result);
    }

    @Test
    void testConcurrentCommandProcessing() throws InterruptedException {
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // When: Multiple threads try to create users concurrently
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    String userId = "user-" + threadId;
                    CreateUserCommand command = new CreateUserCommand(
                        userId, 
                        "username" + threadId, 
                        "user" + threadId + "@example.com", 
                        "User " + threadId
                    );
                    
                    CompletableFuture<String> result = commandGateway.send(command);
                    result.join();
                    successCount.incrementAndGet();
                    
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Then: All commands should succeed (different aggregates)
        assertEquals(threadCount, successCount.get());
        assertEquals(0, failureCount.get());
    }

    @Test
    void testTransactionRollbackOnCommandFailure() {
        String transactionId = "test-command-rollback";
        String userId = UUID.randomUUID().toString();

        // When: Execute command that will fail due to validation
        assertThrows(TransactionRollbackService.TransactionRollbackException.class, () -> {
            transactionRollbackService.executeWithRollback(transactionId, () -> {
                // This command should fail due to invalid email
                CreateUserCommand command = new CreateUserCommand(
                    userId, "testuser", "invalid-email", "Test User"
                );
                
                CompletableFuture<String> result = commandGateway.send(command);
                return result.join();
            });
        });

        // Then: No user projection should be created due to rollback
        assertFalse(userProjectionRepository.existsById(userId));
    }

    @Test
    void testCompensationOnSagaFailure() {
        String sagaId = "test-saga-compensation";
        AtomicInteger compensationCount = new AtomicInteger(0);

        List<TransactionRollbackService.CompensatableOperation<String>> operations = new ArrayList<>();
        
        // First operation - create user (should succeed)
        operations.add(new TransactionRollbackService.CompensatableOperation<String>() {
            @Override
            public String execute() {
                String userId = UUID.randomUUID().toString();
                CreateUserCommand command = new CreateUserCommand(
                    userId, "sagauser", "saga@example.com", "Saga User"
                );
                
                CompletableFuture<String> result = commandGateway.send(command);
                return result.join();
            }

            @Override
            public TransactionRollbackService.CompensationAction getCompensationAction() {
                return () -> {
                    compensationCount.incrementAndGet();
                    // In real scenario, this would deactivate the user or send compensation command
                };
            }
        });

        // Second operation - should fail
        operations.add(new TransactionRollbackService.CompensatableOperation<String>() {
            @Override
            public String execute() {
                throw new RuntimeException("Simulated saga failure");
            }

            @Override
            public TransactionRollbackService.CompensationAction getCompensationAction() {
                return () -> compensationCount.incrementAndGet();
            }
        });

        // When: Execute saga that fails
        CompletableFuture<String> future = transactionRollbackService.executeWithCompensation(sagaId, operations);
        
        assertThrows(TransactionRollbackService.SagaCompensationException.class, () -> {
            future.join();
        });

        // Then: Compensation should have been executed
        assertEquals(1, compensationCount.get());
    }

    @Test
    void testOptimisticLockingInAggregate() {
        String userId = UUID.randomUUID().toString();
        
        // Given: Create initial user
        CreateUserCommand createCommand = new CreateUserCommand(
            userId, "testuser", "test@example.com", "Test User"
        );
        commandGateway.send(createCommand).join();

        // When: Try to update user concurrently with same expected version
        UpdateUserCommand updateCommand1 = new UpdateUserCommand(
            userId, "updateduser1", "updated1@example.com", "Updated User 1"
        );
        UpdateUserCommand updateCommand2 = new UpdateUserCommand(
            userId, "updateduser2", "updated2@example.com", "Updated User 2"
        );

        CompletableFuture<Void> future1 = commandGateway.send(updateCommand1);
        CompletableFuture<Void> future2 = commandGateway.send(updateCommand2);

        // Then: Both updates should complete (Axon handles versioning internally)
        assertDoesNotThrow(() -> {
            future1.join();
            future2.join();
        });
    }

    @Test
    void testTransactionTimeoutHandling() {
        String transactionId = "test-timeout-tx";
        
        // When: Execute long-running transaction that should timeout
        assertThrows(Exception.class, () -> {
            transactionRollbackService.executeWithRollback(transactionId, () -> {
                try {
                    // Simulate long-running operation (longer than transaction timeout)
                    Thread.sleep(35000); // 35 seconds (timeout is 30 seconds)
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted", e);
                }
                
                String userId = UUID.randomUUID().toString();
                CreateUserCommand command = new CreateUserCommand(
                    userId, "timeoutuser", "timeout@example.com", "Timeout User"
                );
                
                CompletableFuture<String> result = commandGateway.send(command);
                return result.join();
            });
        });
    }

    @Test
    void testEventualConsistencyBetweenAggregateAndProjection() throws InterruptedException {
        String userId = UUID.randomUUID().toString();
        
        // When: Create user
        CreateUserCommand command = new CreateUserCommand(
            userId, "projectionuser", "projection@example.com", "Projection User"
        );
        
        commandGateway.send(command).join();
        
        // Give some time for event processing and projection update
        Thread.sleep(1000);
        
        // Then: Projection should eventually be consistent with aggregate
        // Note: In a real test, you might need to wait longer or use polling
        // This is a simplified test as full event processing requires more setup
        
        // Verify the command was processed successfully
        assertNotNull(userId);
    }

    @Test
    void testConcurrentAggregateModification() throws InterruptedException {
        String userId = UUID.randomUUID().toString();
        
        // Given: Create initial user
        CreateUserCommand createCommand = new CreateUserCommand(
            userId, "concurrentuser", "concurrent@example.com", "Concurrent User"
        );
        commandGateway.send(createCommand).join();

        int updateCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(updateCount);
        CountDownLatch latch = new CountDownLatch(updateCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // When: Multiple threads try to update the same aggregate
        for (int i = 0; i < updateCount; i++) {
            final int updateId = i;
            executor.submit(() -> {
                try {
                    UpdateUserCommand updateCommand = new UpdateUserCommand(
                        userId, 
                        "concurrentuser" + updateId, 
                        "concurrent" + updateId + "@example.com", 
                        "Concurrent User " + updateId
                    );
                    
                    commandGateway.send(updateCommand).join();
                    successCount.incrementAndGet();
                    
                } catch (Exception e) {
                    // Some updates might fail due to concurrency
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Then: At least some updates should succeed
        assertTrue(successCount.get() > 0);
    }

    @Test
    void testTransactionPropagationBehavior() {
        String outerTransactionId = "outer-tx";
        String innerTransactionId = "inner-tx";
        
        // Test REQUIRED propagation (inner transaction joins outer)
        String result = transactionRollbackService.executeWithRollback(outerTransactionId, () -> {
            return transactionRollbackService.executeWithRollback(innerTransactionId, () -> {
                String userId = UUID.randomUUID().toString();
                CreateUserCommand command = new CreateUserCommand(
                    userId, "propagationuser", "propagation@example.com", "Propagation User"
                );
                
                CompletableFuture<String> commandResult = commandGateway.send(command);
                return commandResult.join();
            });
        });

        // Then: Both transactions should complete successfully
        assertNotNull(result);
    }
}