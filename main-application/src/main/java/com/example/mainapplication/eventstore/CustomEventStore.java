package com.example.mainapplication.eventstore;

import com.example.mainapplication.service.CustomServerEventPublisher;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom EventStore that forwards all appended events to the external custom server
 * and appends to an internal in-memory storage engine, but does NOT publish to local handlers
 * unless they come from Kafka (with fromKafka=true metadata).
 */
public class CustomEventStore extends EmbeddedEventStore {

    private final CustomServerEventPublisher publisher;
    private final EventStorageEngine storageEngine;

    public CustomEventStore(CustomServerEventPublisher publisher) {
        this(new InMemoryEventStorageEngine(), publisher);
    }

    private CustomEventStore(EventStorageEngine storageEngine, CustomServerEventPublisher publisher) {
        super(EmbeddedEventStore.builder().storageEngine(storageEngine));
        this.storageEngine = storageEngine;
        this.publisher = publisher;
    }

    @Override
    public void publish(List<? extends EventMessage<?>> events) {
        // Separate events by origin
        List<EventMessage<?>> fromKafka = new ArrayList<>();
        List<EventMessage<?>> fromLocal = new ArrayList<>();
        
        for (EventMessage<?> event : events) {
            if (Boolean.TRUE.equals(event.getMetaData().get("fromKafka"))) {
                fromKafka.add(event);
            } else {
                fromLocal.add(event);
            }
        }
        
        // 1) For local events: forward to custom server and append to storage (no local dispatch)
        for (EventMessage<?> event : fromLocal) {
            try {
                publisher.publishEvent(event);
            } catch (Exception ignored) {
                // Do not break command handling flow; publisher handles logging/fallback
            }
        }
        if (!fromLocal.isEmpty()) {
            storageEngine.appendEvents(fromLocal);
        }
        
        // 2) For Kafka events: dispatch to local handlers (these came from the custom server)
        if (!fromKafka.isEmpty()) {
            super.publish(fromKafka);
        }
    }
}
