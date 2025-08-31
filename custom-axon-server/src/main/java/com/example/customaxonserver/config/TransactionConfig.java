package com.example.customaxonserver.config;

import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Utility methods for inspecting Spring transaction state.
 */
public final class TransactionConfig {

    private TransactionConfig() {
        // Utility class; prevent instantiation
    }

    /**
     * Utility method to check if a transaction is active.
     */
    public static boolean isTransactionActive() {
        return TransactionSynchronizationManager.isActualTransactionActive();
    }

    /**
     * Utility method to get current transaction name.
     */
    public static String getCurrentTransactionName() {
        return TransactionSynchronizationManager.getCurrentTransactionName();
    }

    /**
     * Utility method to check if current transaction is read-only.
     */
    public static boolean isCurrentTransactionReadOnly() {
        return TransactionSynchronizationManager.isCurrentTransactionReadOnly();
    }
}