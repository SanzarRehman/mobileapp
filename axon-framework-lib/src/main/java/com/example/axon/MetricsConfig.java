//package com.example.axon.config;
//
//import io.micrometer.core.instrument.Counter;
//import io.micrometer.core.instrument.MeterRegistry;
//import io.micrometer.core.instrument.Timer;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class MetricsConfig {
//
//    @Bean
//    public Counter commandSentCounter(MeterRegistry meterRegistry) {
//        return Counter.builder("main.application.commands.sent")
//                .description("Number of commands sent")
//                .register(meterRegistry);
//    }
//
//    @Bean
//    public Counter querySentCounter(MeterRegistry meterRegistry) {
//        return Counter.builder("main.application.queries.sent")
//                .description("Number of queries sent")
//                .register(meterRegistry);
//    }
//
//    @Bean
//    public Counter eventReceivedCounter(MeterRegistry meterRegistry) {
//        return Counter.builder("main.application.events.received")
//                .description("Number of events received from Kafka")
//                .register(meterRegistry);
//    }
//
//    @Bean
//    public Counter projectionUpdatedCounter(MeterRegistry meterRegistry) {
//        return Counter.builder("main.application.projections.updated")
//                .description("Number of projection updates")
//                .register(meterRegistry);
//    }
//
//    @Bean
//    public Timer commandProcessingTimer(MeterRegistry meterRegistry) {
//        return Timer.builder("main.application.commands.processing.time")
//                .description("Time taken to process commands")
//                .register(meterRegistry);
//    }
//
//    @Bean
//    public Timer queryProcessingTimer(MeterRegistry meterRegistry) {
//        return Timer.builder("main.application.queries.processing.time")
//                .description("Time taken to process queries")
//                .register(meterRegistry);
//    }
//
//    @Bean
//    public Timer eventProcessingTimer(MeterRegistry meterRegistry) {
//        return Timer.builder("main.application.events.processing.time")
//                .description("Time taken to process events")
//                .register(meterRegistry);
//    }
//}