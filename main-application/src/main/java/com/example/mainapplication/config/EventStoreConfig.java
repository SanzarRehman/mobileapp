package com.example.mainapplication.config;

import com.example.mainapplication.eventstore.CustomEventStore;
import com.example.mainapplication.service.CustomServerEventPublisher;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventStoreConfig {

    private static final Logger logger = LoggerFactory.getLogger(EventStoreConfig.class);

    // Provide an EventStore that forwards appends to the custom server and keeps a local in-memory delegate
    @Bean
    public EventStore eventStore(CustomServerEventPublisher publisher) {
        logger.info("Configuring CustomEventStore to forward all events to the custom server");
        return new CustomEventStore(publisher);
    }
}
