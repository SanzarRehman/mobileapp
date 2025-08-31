package com.example.customaxonserver.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter commandProcessedCounter(MeterRegistry meterRegistry) {
        return Counter.builder("custom.axon.server.commands.processed")
                .description("Number of commands processed")
                .register(meterRegistry);
    }

    @Bean
    public Counter queryProcessedCounter(MeterRegistry meterRegistry) {
        return Counter.builder("custom.axon.server.queries.processed")
                .description("Number of queries processed")
                .register(meterRegistry);
    }

    @Bean
    public Counter eventStoredCounter(MeterRegistry meterRegistry) {
        return Counter.builder("custom.axon.server.events.stored")
                .description("Number of events stored")
                .register(meterRegistry);
    }

    @Bean
    public Counter eventPublishedCounter(MeterRegistry meterRegistry) {
        return Counter.builder("custom.axon.server.events.published")
                .description("Number of events published to Kafka")
                .register(meterRegistry);
    }

    @Bean
    public Timer commandProcessingTimer(MeterRegistry meterRegistry) {
        return Timer.builder("custom.axon.server.commands.processing.time")
                .description("Time taken to process commands")
                .register(meterRegistry);
    }

    @Bean
    public Timer queryProcessingTimer(MeterRegistry meterRegistry) {
        return Timer.builder("custom.axon.server.queries.processing.time")
                .description("Time taken to process queries")
                .register(meterRegistry);
    }

    @Bean
    public Timer eventStorageTimer(MeterRegistry meterRegistry) {
        return Timer.builder("custom.axon.server.events.storage.time")
                .description("Time taken to store events")
                .register(meterRegistry);
    }
}