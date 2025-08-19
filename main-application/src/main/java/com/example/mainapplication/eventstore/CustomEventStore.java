package com.example.mainapplication.eventstore;

import com.example.mainapplication.service.CustomServerEventPublisher;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;

import java.util.List;

/**
 * Custom EventStore that forwards all appended events to the external custom server
 * and delegates to an internal in-memory EmbeddedEventStore for local sourcing.
 */
public class CustomEventStore extends EmbeddedEventStore {

    private final CustomServerEventPublisher publisher;

    public CustomEventStore(CustomServerEventPublisher publisher) {
        super(EmbeddedEventStore.builder().storageEngine(new InMemoryEventStorageEngine()));
        this.publisher = publisher;
    }

    @Override
    public void publish(List<? extends EventMessage<?>> events) {
        for (EventMessage<?> event : events) {
            try {
                if (!Boolean.TRUE.equals(event.getMetaData().get("fromKafka"))) {
                    publisher.publishEvent(event);
                }
            } catch (Exception ignored) {
                // Do not break command handling flow; publisher handles logging/fallback
            }
        }
        super.publish(events);
    }
}
