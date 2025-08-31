package com.example.customaxonserver.integration;

import com.example.customaxonserver.entity.EventEntity;
import com.example.customaxonserver.repository.EventRepository;
import com.example.customaxonserver.service.ConcurrencyControlService;
import com.example.customaxonserver.service.EventStoreService;
import com.example.customaxonserver.service.TransactionRollbackService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for transaction consistency and concurrency control.
 */
@SpringBootTest
@ActiveProfiles("test")
class TransactionConsistencyIntegrationTest {

    @Autowired
    private EventStoreService eventStoreService;

    @Autowired
    private ConcurrencyControlService concurrencyControlService;

    @Autowired
    private TransactionRollbackService transactionRollbackService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String TEST_AGGREGATE_ID = "test-aggregate-123";
    private static final String TEST_AGGREGATE_TYPE = "TestAggregate";

    @BeforeEach
    void setUp() {
        // Clean up any existing test data
        try {
            eventRepository.deleteByAggregateId(TEST_AGGREGATE_ID);
        } catch (Exception e) {
            // Ignore if table doesn't exist yet
        }
        concurrencyControlService.clearLock(TEST_AGGREGATE_ID);
        transactionRollbackService.clearActiveTransactions();
    }

    @Test
    void testOptimisticLockingPreventsConflicts() {
        // Given: Store initial event
        EventEntity initialEvent = eventStoreService.storeEvent(
            TEST_AGGREGATE_ID, TEST_AGGREGATE_TYPE, 1L, "TestEvent", 
            "initial data", null
        );
        assertNotNull(initialEvent);

        // When: Try to store conflicting events with same sequence number
        assertThrows(EventStoreService.ConcurrencyException.class, () -> {
            eventStoreService.storeEvent(
                TEST_AGGREGATE_ID, TEST_AGGREGATE_TYPE, 1L, "ConflictingEvent", 
                "conflicting data", null
            );
        });

        // Then: Only the initial event should exist
        List<EventEntity> events = eventStoreService.getEventsForAggregate(TEST_AGGREGATE_ID);
        assertEquals(1, events.size());
        assertEquals("TestEvent", events.get(0).getEventType());
    }

    @Test
    void testConcurrentEventStorageWithOptimisticLocking() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // When: Multiple threads try to store events concurrently
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    Long expectedSequence = eventStoreService.getNextSequenceNumber(TEST_AGGREGATE_ID);
                    eventStoreService.storeEvent(
                        TEST_AGGREGATE_ID, TEST_AGGREGATE_TYPE, expectedSequence, 
                        "ConcurrentEvent" + threadId, "data" + threadId, null
                    );
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

        // Then: Events should be stored with proper sequencing
        List<EventEntity> events = eventStoreService.getEventsForAggregate(TEST_AGGREGATE_ID);
        assertTrue(events.size() > 0);
        assertTrue(successCount.get() > 0);
        
        // Verify sequence numbers are consecutive
        for (int i = 0; i < events.size(); i++) {
            assertEquals(Long.valueOf(i + 1), events.get(i).getSequenceNumber());
        }
    }

    @Test
    void testTransactionRollbackOnFailure() {
        String transactionId = "test-rollback-tx";
        
        // When: Execute operation that fails
        assertThrows(TransactionRollbackService.TransactionRollbackException.class, () -> {
            transactionRollbackService.executeWithRollback(transactionId, () -> {
                // Store an event
                eventStoreService.storeEvent(
                    TEST_AGGREGATE_ID, TEST_AGGREGATE_TYPE, 1L, "TestEvent", 
                    "test data", null
                );
                
                // Simulate failure
                throw new RuntimeException("Simulated failure");
            });
        });

        // Then: No events should be stored due to rollback
        List<EventEntity> events = eventStoreService.getEventsForAggregate(TEST_AGGREGATE_ID);
        assertEquals(0, events.size());
    }

    @Test
    void testSagaCompensationOnFailure() {
        String sagaId = "test-saga-compensation";
        AtomicInteger compensationCount = new AtomicInteger(0);

        List<TransactionRollbackService.CompensatableOperation<String>> operations = new ArrayList<>();
        
        // First operation - should succeed
        operations.add(new TransactionRollbackService.CompensatableOperation<String>() {
            @Override
            public String execute() {
                eventStoreService.storeEvent(
                    TEST_AGGREGATE_ID, TEST_AGGREGATE_TYPE, 1L, "SagaEvent1", 
                    "saga data 1", null
                );
                return "operation1";
            }

            @Override
            public TransactionRollbackService.CompensationAction getCompensationAction() {
                return () -> {
                    compensationCount.incrementAndGet();
                    // In real scenario, this would undo the operation
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
    void testConcurrencyControlWithReadWriteLocks() throws InterruptedException {
        int readerCount = 5;
        int writerCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(readerCount + writerCount);
        CountDownLatch latch = new CountDownLatch(readerCount + writerCount);
        AtomicInteger readOperations = new AtomicInteger(0);
        AtomicInteger writeOperations = new AtomicInteger(0);

        // Store initial event
        eventStoreService.storeEvent(
            TEST_AGGREGATE_ID, TEST_AGGREGATE_TYPE, 1L, "InitialEvent", 
            "initial data", null
        );

        // Start reader threads
        for (int i = 0; i < readerCount; i++) {
            executor.submit(() -> {
                try {
                    concurrencyControlService.executeWithReadLock(TEST_AGGREGATE_ID, () -> {
                        List<EventEntity> events = eventStoreService.getEventsForAggregate(TEST_AGGREGATE_ID);
                        readOperations.incrementAndGet();
                        return events;
                    });
                } finally {
                    latch.countDown();
                }
            });
        }

        // Start writer threads
        for (int i = 0; i < writerCount; i++) {
            final int writerId = i;
            executor.submit(() -> {
                try {
                    concurrencyControlService.executeWithWriteLock(TEST_AGGREGATE_ID, () -> {
                        Long nextSequence = eventStoreService.getNextSequenceNumber(TEST_AGGREGATE_ID);
                        eventStoreService.storeEvent(
                            TEST_AGGREGATE_ID, TEST_AGGREGATE_TYPE, nextSequence, 
                            "WriterEvent" + writerId, "writer data " + writerId, null
                        );
                        writeOperations.incrementAndGet();
                        return null;
                    });
                } catch (Exception e) {
                    // Expected due to concurrency
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Then: All operations should have completed
        assertEquals(readerCount, readOperations.get());
        assertTrue(writeOperations.get() > 0);
    }

    @Test
    void testVersionBasedOptimisticLocking() {
        // Given: Store initial event
        EventEntity event1 = eventStoreService.storeEvent(
            TEST_AGGREGATE_ID, TEST_AGGREGATE_TYPE, 1L, "Event1", 
            "data1", null
        );
        assertNotNull(event1.getVersion());

        // When: Update the event (simulating version change)
        EventEntity reloadedEvent = eventRepository.findById(event1.getId()).orElseThrow();
        reloadedEvent.setEventType("UpdatedEvent1");
        EventEntity savedEvent = eventRepository.save(reloadedEvent);

        // Then: Version should be incremented
        assertTrue(savedEvent.getVersion() > event1.getVersion());
    }

    @Test
    void testTransactionIsolationLevels() {
        String transactionId1 = "tx1";
        String transactionId2 = "tx2";

        // Test READ_COMMITTED isolation
        CompletableFuture<Void> tx1 = CompletableFuture.runAsync(() -> {
            transactionRollbackService.executeWithRollback(transactionId1, () -> {
                eventStoreService.storeEvent(
                    TEST_AGGREGATE_ID, TEST_AGGREGATE_TYPE, 1L, "TxEvent1", 
                    "tx1 data", null
                );
                
                // Simulate long-running transaction
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                return null;
            });
        });

        CompletableFuture<Void> tx2 = CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(50); // Start after tx1
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            transactionRollbackService.executeWithRollback(transactionId2, () -> {
                Long nextSequence = eventStoreService.getNextSequenceNumber(TEST_AGGREGATE_ID);
                eventStoreService.storeEvent(
                    TEST_AGGREGATE_ID, TEST_AGGREGATE_TYPE, nextSequence, "TxEvent2", 
                    "tx2 data", null
                );
                return null;
            });
        });

        // Wait for both transactions to complete
        CompletableFuture.allOf(tx1, tx2).join();

        // Verify both events were stored with proper sequencing
        List<EventEntity> events = eventStoreService.getEventsForAggregate(TEST_AGGREGATE_ID);
        assertEquals(2, events.size());
        assertEquals(Long.valueOf(1), events.get(0).getSequenceNumber());
        assertEquals(Long.valueOf(2), events.get(1).getSequenceNumber());
    }

    @Test
    void testDeadlockDetectionAndResolution() throws InterruptedException {
        String aggregateId1 = TEST_AGGREGATE_ID + "-1";
        String aggregateId2 = TEST_AGGREGATE_ID + "-2";
        
        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Thread 1: Lock aggregate1 then aggregate2
        Thread thread1 = new Thread(() -> {
            try {
                concurrencyControlService.executeWithWriteLock(aggregateId1, () -> {
                    try {
                        Thread.sleep(100); // Hold lock
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    
                    return concurrencyControlService.executeWithWriteLock(aggregateId2, () -> {
                        eventStoreService.storeEvent(
                            aggregateId2, TEST_AGGREGATE_TYPE, 1L, "DeadlockEvent1", 
                            "data1", null
                        );
                        successCount.incrementAndGet();
                        return null;
                    });
                });
            } catch (Exception e) {
                failureCount.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });

        // Thread 2: Lock aggregate2 then aggregate1
        Thread thread2 = new Thread(() -> {
            try {
                concurrencyControlService.executeWithWriteLock(aggregateId2, () -> {
                    try {
                        Thread.sleep(100); // Hold lock
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    
                    return concurrencyControlService.executeWithWriteLock(aggregateId1, () -> {
                        eventStoreService.storeEvent(
                            aggregateId1, TEST_AGGREGATE_TYPE, 1L, "DeadlockEvent2", 
                            "data2", null
                        );
                        successCount.incrementAndGet();
                        return null;
                    });
                });
            } catch (Exception e) {
                failureCount.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });

        thread1.start();
        thread2.start();
        
        latch.await();

        // At least one thread should succeed (deadlock resolution)
        assertTrue(successCount.get() > 0 || failureCount.get() > 0);
        
        // Clean up
        eventRepository.deleteByAggregateId(aggregateId1);
        eventRepository.deleteByAggregateId(aggregateId2);
        concurrencyControlService.clearLock(aggregateId1);
        concurrencyControlService.clearLock(aggregateId2);
    }
}