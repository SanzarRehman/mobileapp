package com.example.mainapplication.config;

import org.axonframework.config.EventProcessingConfigurer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

/**
 * Axon Framework configuration for the main application.
 * Configures interceptors to work with Axon's auto-configuration.
 */
@Configuration
public class AxonConfig {

    // Keep this class for future Axon-specific configurations.

    @Autowired
    public void configureEventProcessing(EventProcessingConfigurer configurer) {
        // No default handler interceptors registered here to avoid double-publishing.
        // Event forwarding is handled via EventBus dispatch interceptor in EventStoreConfig.
    }
}