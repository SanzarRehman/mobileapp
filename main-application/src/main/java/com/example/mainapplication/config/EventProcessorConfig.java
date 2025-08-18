package com.example.mainapplication.config;

import org.axonframework.config.EventProcessingConfigurer;
import org.axonframework.eventhandling.TrackingEventProcessorConfiguration;
import org.axonframework.messaging.StreamableMessageSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Axon event processors to handle transaction issues
 * and improve error handling for saga processing.
 */
@Configuration
public class EventProcessorConfig {

    @Autowired
    public void configure(EventProcessingConfigurer configurer) {
        // Configure the UserManagementSagaProcessor specifically
        configurer.registerTrackingEventProcessorConfiguration("UserManagementSagaProcessor",
                c -> TrackingEventProcessorConfiguration.forParallelProcessing(1)
                        .andBatchSize(1)
                        .andInitialTrackingToken(StreamableMessageSource::createHeadToken));
        
        // Configure other event processors with improved settings
        configurer.registerTrackingEventProcessorConfiguration("com.example.mainapplication.handler",
                c -> TrackingEventProcessorConfiguration.forParallelProcessing(1)
                        .andBatchSize(1)
                        .andInitialTrackingToken(StreamableMessageSource::createHeadToken));
    }
}