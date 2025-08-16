package com.example.customaxonserver.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Configuration for distributed transaction management.
 * Provides transaction coordination across multiple resources.
 */
@Configuration
@EnableTransactionManagement
public class TransactionConfig {



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