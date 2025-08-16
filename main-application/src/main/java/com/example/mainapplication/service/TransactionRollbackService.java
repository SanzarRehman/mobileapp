package com.example.mainapplication.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Service for managing transaction rollback mechanisms and failure scenarios in main application.
 * Provides utilities for handling distributed transaction failures and compensation.
 */
@Service
public class TransactionRollbackService {

    private static final Logger logger = LoggerFactory.getLogger(TransactionRollbackService.class);

    private final PlatformTransactionManager transactionManager;
    private final TransactionTemplate transactionTemplate;
    
    // Track active transactions for rollback purposes
    private final ConcurrentHashMap<String, TransactionContext> activeTransactions = new ConcurrentHashMap<>();

    public TransactionRollbackService(PlatformTransactionManager transactionManager,
                                    TransactionTemplate transactionTemplate) {
        this.transactionManager = transactionManager;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Executes an operation within a transaction with automatic rollback on failure.
     */
    public <T> T executeWithRollback(String transactionId, Supplier<T> operation) {
        logger.debug("Starting transaction with rollback support: {}", transactionId);
        
        TransactionContext context = new TransactionContext(transactionId);
        activeTransactions.put(transactionId, context);
        
        try {
            T result = transactionTemplate.execute(status -> {
                context.setTransactionStatus(status);
                
                try {
                    T operationResult = operation.get();
                    context.markSuccess();
                    logger.debug("Transaction {} completed successfully", transactionId);
                    return operationResult;
                    
                } catch (Exception e) {
                    logger.error("Transaction {} failed, marking for rollback", transactionId, e);
                    context.markFailure(e);
                    status.setRollbackOnly();
                    throw new TransactionRollbackException("Transaction failed: " + transactionId, e);
                }
            });
            
            return result;
            
        } finally {
            activeTransactions.remove(transactionId);
        }
    }

    /**
     * Executes multiple operations in separate transactions with compensation on failure.
     */
    public <T> CompletableFuture<T> executeWithCompensation(String sagaId, 
                                                           List<CompensatableOperation<T>> operations) {
        logger.info("Starting compensatable transaction saga: {}", sagaId);
        
        return CompletableFuture.supplyAsync(() -> {
            List<CompensationAction> compensationActions = new ArrayList<>();
            
            try {
                T result = null;
                
                for (int i = 0; i < operations.size(); i++) {
                    CompensatableOperation<T> operation = operations.get(i);
                    String operationId = sagaId + "-op-" + i;
                    
                    logger.debug("Executing operation {} in saga {}", i, sagaId);
                    
                    try {
                        result = executeWithRollback(operationId, operation::execute);
                        
                        // Add compensation action for successful operation
                        if (operation.getCompensationAction() != null) {
                            compensationActions.add(operation.getCompensationAction());
                        }
                        
                    } catch (Exception e) {
                        logger.error("Operation {} failed in saga {}, starting compensation", i, sagaId, e);
                        
                        // Execute compensation for all previously successful operations
                        executeCompensation(sagaId, compensationActions);
                        
                        throw new SagaCompensationException("Saga failed at operation " + i, e);
                    }
                }
                
                logger.info("Saga {} completed successfully", sagaId);
                return result;
                
            } catch (Exception e) {
                logger.error("Saga {} failed completely", sagaId, e);
                throw e;
            }
        });
    }

    /**
     * Executes compensation actions in reverse order.
     */
    private void executeCompensation(String sagaId, List<CompensationAction> compensationActions) {
        logger.info("Executing compensation for saga: {}", sagaId);
        
        // Execute compensation actions in reverse order
        for (int i = compensationActions.size() - 1; i >= 0; i--) {
            CompensationAction action = compensationActions.get(i);
            String compensationId = sagaId + "-compensation-" + i;
            
            try {
                logger.debug("Executing compensation action {} for saga {}", i, sagaId);
                
                executeWithRollback(compensationId, () -> {
                    action.compensate();
                    return null;
                });
                
                logger.debug("Compensation action {} completed for saga {}", i, sagaId);
                
            } catch (Exception e) {
                logger.error("Compensation action {} failed for saga {}", i, sagaId, e);
                // Continue with other compensation actions even if one fails
            }
        }
        
        logger.info("Compensation completed for saga: {}", sagaId);
    }

    /**
     * Forces rollback of an active transaction.
     */
    public void forceRollback(String transactionId) {
        TransactionContext context = activeTransactions.get(transactionId);
        
        if (context != null && context.getTransactionStatus() != null) {
            logger.warn("Forcing rollback of transaction: {}", transactionId);
            context.getTransactionStatus().setRollbackOnly();
            context.markFailure(new RuntimeException("Forced rollback"));
        } else {
            logger.warn("Cannot force rollback - transaction not found or not active: {}", transactionId);
        }
    }

    /**
     * Checks if a transaction is marked for rollback.
     */
    public boolean isMarkedForRollback(String transactionId) {
        TransactionContext context = activeTransactions.get(transactionId);
        return context != null && 
               context.getTransactionStatus() != null && 
               context.getTransactionStatus().isRollbackOnly();
    }

    /**
     * Gets the status of an active transaction.
     */
    public TransactionStatus getTransactionStatus(String transactionId) {
        TransactionContext context = activeTransactions.get(transactionId);
        return context != null ? context.getTransactionStatus() : null;
    }

    /**
     * Gets the number of active transactions.
     */
    public int getActiveTransactionCount() {
        return activeTransactions.size();
    }

    /**
     * Clears all active transaction tracking (for testing).
     */
    public void clearActiveTransactions() {
        activeTransactions.clear();
    }

    /**
     * Context for tracking transaction state.
     */
    private static class TransactionContext {
        private final String transactionId;
        private TransactionStatus transactionStatus;
        private boolean successful = false;
        private Exception failure;
        private final long startTime;

        public TransactionContext(String transactionId) {
            this.transactionId = transactionId;
            this.startTime = System.currentTimeMillis();
        }

        public void setTransactionStatus(TransactionStatus status) {
            this.transactionStatus = status;
        }

        public void markSuccess() {
            this.successful = true;
        }

        public void markFailure(Exception e) {
            this.successful = false;
            this.failure = e;
        }

        // Getters
        public String getTransactionId() { return transactionId; }
        public TransactionStatus getTransactionStatus() { return transactionStatus; }
        public boolean isSuccessful() { return successful; }
        public Exception getFailure() { return failure; }
        public long getStartTime() { return startTime; }
    }

    /**
     * Interface for operations that can be compensated.
     */
    public interface CompensatableOperation<T> {
        T execute();
        CompensationAction getCompensationAction();
    }

    /**
     * Interface for compensation actions.
     */
    public interface CompensationAction {
        void compensate();
    }

    /**
     * Exception thrown when transaction rollback occurs.
     */
    public static class TransactionRollbackException extends RuntimeException {
        public TransactionRollbackException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when saga compensation occurs.
     */
    public static class SagaCompensationException extends RuntimeException {
        public SagaCompensationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}