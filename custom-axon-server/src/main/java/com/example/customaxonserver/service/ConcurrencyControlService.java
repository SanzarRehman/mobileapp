package com.example.customaxonserver.service;

import com.example.customaxonserver.exception.EventStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * Service for managing concurrency control and optimistic locking.
 * Provides utilities for handling concurrent access to aggregates.
 */
@Service
public class ConcurrencyControlService {

    private static final Logger logger = LoggerFactory.getLogger(ConcurrencyControlService.class);
    
    // In-memory locks for aggregate-level concurrency control
    private final ConcurrentHashMap<String, ReentrantReadWriteLock> aggregateLocks = new ConcurrentHashMap<>();
    
    /**
     * Executes an operation with optimistic locking retry logic.
     */
    @Retryable(
        value = {OptimisticLockingFailureException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional(
        propagation = Propagation.REQUIRED,
        rollbackFor = Exception.class
    )
    public <T> T executeWithOptimisticLocking(String aggregateId, Supplier<T> operation) {
        logger.debug("Executing operation with optimistic locking for aggregate: {}", aggregateId);
        
        try {
            return operation.get();
        } catch (OptimisticLockingFailureException e) {
            logger.warn("Optimistic locking failure for aggregate: {}. Retrying...", aggregateId);
            throw e; // Will be retried by @Retryable
        } catch (Exception e) {
            logger.error("Error executing operation for aggregate: {}", aggregateId, e);
            throw new EventStoreException("Failed to execute operation for aggregate: " + aggregateId, e);
        }
    }

    /**
     * Executes an operation with aggregate-level read lock.
     */
    public <T> T executeWithReadLock(String aggregateId, Supplier<T> operation) {
        ReentrantReadWriteLock lock = getOrCreateLock(aggregateId);
        lock.readLock().lock();
        
        try {
            logger.debug("Acquired read lock for aggregate: {}", aggregateId);
            return operation.get();
        } finally {
            lock.readLock().unlock();
            logger.debug("Released read lock for aggregate: {}", aggregateId);
        }
    }

    /**
     * Executes an operation with aggregate-level write lock.
     */
    public <T> T executeWithWriteLock(String aggregateId, Supplier<T> operation) {
        ReentrantReadWriteLock lock = getOrCreateLock(aggregateId);
        lock.writeLock().lock();
        
        try {
            logger.debug("Acquired write lock for aggregate: {}", aggregateId);
            return operation.get();
        } finally {
            lock.writeLock().unlock();
            logger.debug("Released write lock for aggregate: {}", aggregateId);
        }
    }

    /**
     * Executes an operation with both optimistic locking and write lock.
     */
    @Retryable(
        value = {OptimisticLockingFailureException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional(
        propagation = Propagation.REQUIRED,
        rollbackFor = Exception.class
    )
    public <T> T executeWithFullConcurrencyControl(String aggregateId, Supplier<T> operation) {
        return executeWithWriteLock(aggregateId, () -> executeWithOptimisticLocking(aggregateId, operation));
    }

    /**
     * Validates expected version against actual version.
     */
    public void validateVersion(String aggregateId, Long expectedVersion, Long actualVersion) {
        if (expectedVersion != null && !expectedVersion.equals(actualVersion)) {
            String message = String.format(
                "Version mismatch for aggregate %s: expected %d, actual %d", 
                aggregateId, expectedVersion, actualVersion
            );
            logger.warn(message);
            throw new OptimisticLockingFailureException(message);
        }
    }

    /**
     * Checks if an aggregate is currently locked for writing.
     */
    public boolean isWriteLocked(String aggregateId) {
        ReentrantReadWriteLock lock = aggregateLocks.get(aggregateId);
        return lock != null && lock.isWriteLocked();
    }

    /**
     * Gets the number of read locks for an aggregate.
     */
    public int getReadLockCount(String aggregateId) {
        ReentrantReadWriteLock lock = aggregateLocks.get(aggregateId);
        return lock != null ? lock.getReadLockCount() : 0;
    }

    /**
     * Clears the lock for an aggregate (for testing purposes).
     */
    public void clearLock(String aggregateId) {
        aggregateLocks.remove(aggregateId);
        logger.debug("Cleared lock for aggregate: {}", aggregateId);
    }

    /**
     * Gets or creates a lock for the specified aggregate.
     */
    private ReentrantReadWriteLock getOrCreateLock(String aggregateId) {
        return aggregateLocks.computeIfAbsent(aggregateId, k -> {
            logger.debug("Creating new lock for aggregate: {}", aggregateId);
            return new ReentrantReadWriteLock(true); // Fair lock
        });
    }

    /**
     * Gets the total number of managed locks.
     */
    public int getManagedLockCount() {
        return aggregateLocks.size();
    }
}