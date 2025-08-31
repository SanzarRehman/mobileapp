package com.example.axon.eventstore;

import com.example.axon.service.CustomServerEventFetcher;
import com.example.axon.service.CustomServerEventPublisher;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.messaging.MetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom EventStore that forwards all appended events to the external custom server
 * and appends to an internal in-memory storage engine, but does NOT publish to local handlers
 * unless they come from Kafka (with fromKafka=true metadata).
 * 
 * When reading events, it first checks the local store, and if not found, fetches from custom server.
 */
public class CustomEventStore extends EmbeddedEventStore {

    private static final Logger logger = LoggerFactory.getLogger(CustomEventStore.class);

    private final CustomServerEventPublisher publisher;
    private final CustomServerEventFetcher eventFetcher;
    private final EventStorageEngine storageEngine;

    public CustomEventStore(CustomServerEventPublisher publisher, CustomServerEventFetcher eventFetcher) {
        this(new InMemoryEventStorageEngine(), publisher, eventFetcher);
    }

    private CustomEventStore(EventStorageEngine storageEngine, CustomServerEventPublisher publisher, CustomServerEventFetcher eventFetcher) {
        super(EmbeddedEventStore.builder().storageEngine(storageEngine));
        this.storageEngine = storageEngine;
        this.publisher = publisher;
        this.eventFetcher = eventFetcher;
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

    @Override
    public DomainEventStream readEvents(String aggregateIdentifier) {
        logger.debug("Reading events for aggregate: {}", aggregateIdentifier);
        
        // First try to read from local storage
        DomainEventStream localStream = super.readEvents(aggregateIdentifier);
        
        // Check if we have any events locally
        List<DomainEventMessage<?>> localEvents = new ArrayList<>();
        while (localStream.hasNext()) {
            localEvents.add(localStream.next());
        }
        
        if (!localEvents.isEmpty()) {
            logger.debug("Found {} events locally for aggregate: {}", localEvents.size(), aggregateIdentifier);
            return DomainEventStream.of(localEvents);
        }
        
        // If no local events, fetch from custom server
        logger.debug("No local events found, fetching from custom server for aggregate: {}", aggregateIdentifier);
        List<CustomServerEventFetcher.EventData> serverEvents = eventFetcher.fetchEventsForAggregate(aggregateIdentifier);
        
        if (serverEvents.isEmpty()) {
            logger.debug("No events found on custom server for aggregate: {}", aggregateIdentifier);
            return DomainEventStream.of();
        }
        
        // Convert to DomainEventMessages and store locally for future use
        List<DomainEventMessage<?>> domainEvents = new ArrayList<>();
        
        for (CustomServerEventFetcher.EventData eventData : serverEvents) {
            // Use the original sequence number from the custom server (convert from 1-based to 0-based for Axon)
            long axonSequenceNumber = eventData.getSequenceNumber() - 1;
            
            DomainEventMessage<?> domainEvent = new GenericDomainEventMessage<>(
                eventData.getAggregateType(),
                aggregateIdentifier,
                axonSequenceNumber,
                eventData.getEvent(),
                MetaData.emptyInstance()
            );
            domainEvents.add(domainEvent);
        }
        
        // Store events locally for future reads
        try {
            storageEngine.appendEvents(domainEvents);
            logger.debug("Stored {} events locally from custom server for aggregate: {}", 
                        domainEvents.size(), aggregateIdentifier);
        } catch (Exception e) {
            logger.warn("Failed to store events locally for aggregate {}: {}", aggregateIdentifier, e.getMessage());
        }
        
        return DomainEventStream.of(domainEvents);
    }

    @Override
    public DomainEventStream readEvents(String aggregateIdentifier, long firstSequenceNumber) {
        // For simplicity, read all events and filter by sequence number
        DomainEventStream allEvents = readEvents(aggregateIdentifier);
        List<DomainEventMessage<?>> filteredEvents = new ArrayList<>();
        
        while (allEvents.hasNext()) {
            DomainEventMessage<?> event = allEvents.next();
            if (event.getSequenceNumber() >= firstSequenceNumber) {
                filteredEvents.add(event);
            }
        }
        
        return DomainEventStream.of(filteredEvents);
    }
}
