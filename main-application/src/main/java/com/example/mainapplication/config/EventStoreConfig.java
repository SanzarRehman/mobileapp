package com.example.mainapplication.config;

import com.example.mainapplication.service.CustomServerEventPublisher;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;
import java.util.function.BiFunction;

@Configuration
public class EventStoreConfig {

    private static final Logger logger = LoggerFactory.getLogger(EventStoreConfig.class);

    // Force Axon to use an in-memory event storage in this service to avoid JPA mappings
    @Bean
    public EventStorageEngine eventStorageEngine() {
        return new InMemoryEventStorageEngine();
    }

    // Explicit EmbeddedEventStore bean
    @Bean
    public EmbeddedEventStore eventStore(EventStorageEngine storageEngine) {
        return EmbeddedEventStore.builder().storageEngine(storageEngine).build();
    }

    // Register a dispatch interceptor so each event is forwarded once to the custom server
    @Bean
    @ConditionalOnProperty(prefix = "app.event-forwarding", name = "enabled", havingValue = "true", matchIfMissing = true)
    public MessageDispatchInterceptor<EventMessage<?>> eventForwardingDispatchInterceptor(CustomServerEventPublisher publisher) {
        return new MessageDispatchInterceptor<>() {
            @Override
            public BiFunction<Integer, EventMessage<?>, EventMessage<?>> handle(List<? extends EventMessage<?>> messages) {
                return (index, message) -> {
                    try {
                        publisher.publishEvent(message);
                    } catch (Exception e) {
                        logger.error("Failed to forward event {} to custom server: {}", message.getIdentifier(), e.getMessage(), e);
                    }
                    return message;
                };
            }
        };
    }

    // Hook the interceptor into the EventBus after it is ready
    @Bean
    @ConditionalOnProperty(prefix = "app.event-forwarding", name = "enabled", havingValue = "true", matchIfMissing = true)
    public Object registerEventBusDispatchInterceptor(EventBus eventBus, MessageDispatchInterceptor<EventMessage<?>> interceptor) {
        eventBus.registerDispatchInterceptor(interceptor);
        return new Object();
    }
}
