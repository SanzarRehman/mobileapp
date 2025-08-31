package com.example.axon;


import com.example.axon.eventstore.CustomEventStore;
import com.example.axon.service.CustomServerEventFetcher;
import com.example.axon.service.CustomServerEventPublisher;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventStoreConfig {

    private static final Logger logger = LoggerFactory.getLogger(EventStoreConfig.class);

    // Provide an EventStore that forwards appends to the custom server and fetches reads from custom server
    @Bean
    public EventStore eventStore(CustomServerEventPublisher publisher, CustomServerEventFetcher eventFetcher) {
        logger.info("Configuring CustomEventStore to forward all events to the custom server and fetch from custom server");
        return new CustomEventStore(publisher, eventFetcher);
    }
}
