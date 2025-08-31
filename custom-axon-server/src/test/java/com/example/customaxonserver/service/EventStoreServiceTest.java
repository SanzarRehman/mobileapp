package com.example.customaxonserver.service;

import com.example.customaxonserver.entity.EventEntity;
import com.example.customaxonserver.messaging.DeadLetterQueueHandler;
import com.example.customaxonserver.repository.EventRepository;
import com.example.customaxonserver.resilience.CircuitBreakerService;
import com.example.customaxonserver.resilience.RetryService;
import com.example.customaxonserver.service.EventStoreService.ConcurrencyException;
import com.example.customaxonserver.service.EventStoreService.EventData;
import com.example.customaxonserver.service.EventStoreService.EventStoreException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EventStoreService.
 * Tests event storage, retrieval, concurrency control, and error handling.
 */
@ExtendWith(MockitoExtension.class)
class EventStoreServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private ObjectMapper objectMapper;

    private EventStoreService eventStoreService;

    private static final String AGGREGATE_ID = "test-aggregate-123";
    private static final String AGGREGATE_TYPE = "TestAggregate";
    private static final String EVENT_TYPE = "TestEvent";

    @Mock
    private CircuitBreakerService circuitBreakerService;

    @Mock
    private RetryService retryService;

    @Mock
    private DeadLetterQueueHandler deadLetterQueueHandler;

    @Mock
    private ConcurrencyControlService concurrencyControlService;

    @BeforeEach
    void setUp() {
        eventStoreService = new EventStoreService(eventRepository, objectMapper, 
                                                 circuitBreakerService, retryService, 
                                                 deadLetterQueueHandler, concurrencyControlService);
    }

    @Test
    void storeEvent_WithValidData_ShouldStoreSuccessfully() {
        // Given
        Long expectedSequence = 1L;
        Long currentSequence = 0L;
        Object eventData = new TestEventPayload("test data");
        Object metadata = new TestMetadata("test metadata");
        JsonNode eventDataNode = mock(JsonNode.class);
        JsonNode metadataNode = mock(JsonNode.class);

        EventEntity latestEvent = new EventEntity();
        latestEvent.setSequenceNumber(currentSequence);
        
        EventEntity savedEvent = new EventEntity(AGGREGATE_ID, AGGREGATE_TYPE, expectedSequence, EVENT_TYPE, eventDataNode);
        savedEvent.setId(1L);

        when(eventRepository.findTopByAggregateIdOrderBySequenceNumberDesc(AGGREGATE_ID))
            .thenReturn(Optional.of(latestEvent));
        when(objectMapper.valueToTree(eventData)).thenReturn(eventDataNode);
        when(objectMapper.valueToTree(metadata)).thenReturn(metadataNode);
        when(eventRepository.save(any(EventEntity.class))).thenReturn(savedEvent);
        when(concurrencyControlService.executeWithFullConcurrencyControl(eq(AGGREGATE_ID), any()))
            .thenAnswer(invocation -> invocation.getArgument(1, java.util.function.Supplier.class).get());

        // When
        EventEntity result = eventStoreService.storeEvent(AGGREGATE_ID, AGGREGATE_TYPE, 
                                                         expectedSequence, EVENT_TYPE, eventData, metadata);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getAggregateId()).isEqualTo(AGGREGATE_ID);
        assertThat(result.getSequenceNumber()).isEqualTo(expectedSequence);

        verify(eventRepository).findTopByAggregateIdOrderBySequenceNumberDesc(AGGREGATE_ID);
        verify(objectMapper).valueToTree(eventData);
        verify(objectMapper).valueToTree(metadata);
        verify(eventRepository).save(any(EventEntity.class));
    }

    @Test
    void storeEvent_WithNoExistingEvents_ShouldStoreWithSequenceOne() {
        // Given
        Long expectedSequence = 1L;
        Object eventData = new TestEventPayload("test data");
        JsonNode eventDataNode = mock(JsonNode.class);

        EventEntity savedEvent = new EventEntity(AGGREGATE_ID, AGGREGATE_TYPE, expectedSequence, EVENT_TYPE, eventDataNode);

        when(eventRepository.findTopByAggregateIdOrderBySequenceNumberDesc(AGGREGATE_ID))
            .thenReturn(Optional.empty());
        when(objectMapper.valueToTree(eventData)).thenReturn(eventDataNode);
        when(eventRepository.save(any(EventEntity.class))).thenReturn(savedEvent);

        // When
        EventEntity result = eventStoreService.storeEvent(AGGREGATE_ID, AGGREGATE_TYPE, 
                                                         expectedSequence, EVENT_TYPE, eventData, null);

        // Then
        assertThat(result).isNotNull();
        verify(eventRepository).save(argThat(event -> 
            event.getSequenceNumber().equals(1L) && 
            event.getMetadata() == null
        ));
    }

    @Test
    void storeEvent_WithConcurrencyConflict_ShouldThrowConcurrencyException() {
        // Given
        Long expectedSequence = 2L;
        Long currentSequence = 2L; // Conflict: expected next should be 3
        Object eventData = new TestEventPayload("test data");

        EventEntity latestEvent = new EventEntity();
        latestEvent.setSequenceNumber(currentSequence);

        when(eventRepository.findTopByAggregateIdOrderBySequenceNumberDesc(AGGREGATE_ID))
            .thenReturn(Optional.of(latestEvent));

        // When & Then
        assertThatThrownBy(() -> eventStoreService.storeEvent(AGGREGATE_ID, AGGREGATE_TYPE, 
                                                             expectedSequence, EVENT_TYPE, eventData, null))
            .isInstanceOf(ConcurrencyException.class)
            .hasMessageContaining("Expected sequence number 2 but current is 2");

        verify(eventRepository, never()).save(any());
    }

    @Test
    void storeEvent_WithDataIntegrityViolation_ShouldThrowConcurrencyException() {
        // Given
        Long expectedSequence = 1L;
        Object eventData = new TestEventPayload("test data");
        JsonNode eventDataNode = mock(JsonNode.class);

        when(eventRepository.findTopByAggregateIdOrderBySequenceNumberDesc(AGGREGATE_ID))
            .thenReturn(Optional.empty());
        when(objectMapper.valueToTree(eventData)).thenReturn(eventDataNode);
        when(eventRepository.save(any(EventEntity.class)))
            .thenThrow(new DataIntegrityViolationException("Unique constraint violation"));

        // When & Then
        assertThatThrownBy(() -> eventStoreService.storeEvent(AGGREGATE_ID, AGGREGATE_TYPE, 
                                                             expectedSequence, EVENT_TYPE, eventData, null))
            .isInstanceOf(ConcurrencyException.class)
            .hasMessageContaining("Concurrent modification detected");
    }

    @Test
    void storeEvents_WithValidData_ShouldStoreAllEvents() {
        // Given
        Long startingSequence = 1L;
        List<EventData> events = Arrays.asList(
            new EventData("Event1", new TestEventPayload("data1")),
            new EventData("Event2", new TestEventPayload("data2"), new TestMetadata("meta2"))
        );

        JsonNode eventDataNode1 = mock(JsonNode.class);
        JsonNode eventDataNode2 = mock(JsonNode.class);
        JsonNode metadataNode2 = mock(JsonNode.class);

        when(eventRepository.findTopByAggregateIdOrderBySequenceNumberDesc(AGGREGATE_ID))
            .thenReturn(Optional.empty());
        when(objectMapper.valueToTree(events.get(0).getPayload())).thenReturn(eventDataNode1);
        when(objectMapper.valueToTree(events.get(1).getPayload())).thenReturn(eventDataNode2);
        when(objectMapper.valueToTree(events.get(1).getMetadata())).thenReturn(metadataNode2);
        when(eventRepository.saveAll(anyList())).thenReturn(Arrays.asList(
            new EventEntity(AGGREGATE_ID, AGGREGATE_TYPE, 1L, "Event1", eventDataNode1),
            new EventEntity(AGGREGATE_ID, AGGREGATE_TYPE, 2L, "Event2", eventDataNode2)
        ));
        when(concurrencyControlService.executeWithFullConcurrencyControl(eq(AGGREGATE_ID), any()))
            .thenAnswer(invocation -> invocation.getArgument(1, java.util.function.Supplier.class).get());

        // When
        List<EventEntity> result = eventStoreService.storeEvents(AGGREGATE_ID, AGGREGATE_TYPE, 
                                                                startingSequence, events);

        // Then
        assertThat(result).hasSize(2);
        verify(eventRepository).saveAll(argThat(eventList -> {
            List<EventEntity> eventEntities = (List<EventEntity>) eventList;
            return eventEntities.size() == 2 &&
                   eventEntities.get(0).getSequenceNumber().equals(1L) &&
                   eventEntities.get(1).getSequenceNumber().equals(2L);
        }));
    }

    @Test
    void getEventsForAggregate_ShouldReturnAllEvents() {
        // Given
        List<EventEntity> expectedEvents = Arrays.asList(
            createEventEntity(1L, "Event1"),
            createEventEntity(2L, "Event2")
        );

        when(eventRepository.findByAggregateIdOrderBySequenceNumber(AGGREGATE_ID))
            .thenReturn(expectedEvents);
        when(concurrencyControlService.executeWithReadLock(eq(AGGREGATE_ID), any()))
            .thenAnswer(invocation -> invocation.getArgument(1, java.util.function.Supplier.class).get());

        // When
        List<EventEntity> result = eventStoreService.getEventsForAggregate(AGGREGATE_ID);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).isEqualTo(expectedEvents);
        verify(eventRepository).findByAggregateIdOrderBySequenceNumber(AGGREGATE_ID);
    }

    @Test
    void getEventsForAggregate_WithFromSequence_ShouldReturnEventsFromSequence() {
        // Given
        Long fromSequence = 2L;
        List<EventEntity> expectedEvents = Arrays.asList(
            createEventEntity(2L, "Event2"),
            createEventEntity(3L, "Event3")
        );

        when(eventRepository.findByAggregateIdAndSequenceNumberGreaterThanEqualOrderBySequenceNumber(
            AGGREGATE_ID, fromSequence)).thenReturn(expectedEvents);

        // When
        List<EventEntity> result = eventStoreService.getEventsForAggregate(AGGREGATE_ID, fromSequence);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).isEqualTo(expectedEvents);
        verify(eventRepository).findByAggregateIdAndSequenceNumberGreaterThanEqualOrderBySequenceNumber(
            AGGREGATE_ID, fromSequence);
    }

    @Test
    void getCurrentSequenceNumber_WithExistingEvents_ShouldReturnLatestSequence() {
        // Given
        EventEntity latestEvent = createEventEntity(5L, "LatestEvent");
        when(eventRepository.findTopByAggregateIdOrderBySequenceNumberDesc(AGGREGATE_ID))
            .thenReturn(Optional.of(latestEvent));

        // When
        Long result = eventStoreService.getCurrentSequenceNumber(AGGREGATE_ID);

        // Then
        assertThat(result).isEqualTo(5L);
    }

    @Test
    void getCurrentSequenceNumber_WithNoEvents_ShouldReturnZero() {
        // Given
        when(eventRepository.findTopByAggregateIdOrderBySequenceNumberDesc(AGGREGATE_ID))
            .thenReturn(Optional.empty());

        // When
        Long result = eventStoreService.getCurrentSequenceNumber(AGGREGATE_ID);

        // Then
        assertThat(result).isEqualTo(0L);
    }

    @Test
    void getNextSequenceNumber_ShouldReturnCurrentPlusOne() {
        // Given
        EventEntity latestEvent = createEventEntity(3L, "LatestEvent");
        when(eventRepository.findTopByAggregateIdOrderBySequenceNumberDesc(AGGREGATE_ID))
            .thenReturn(Optional.of(latestEvent));

        // When
        Long result = eventStoreService.getNextSequenceNumber(AGGREGATE_ID);

        // Then
        assertThat(result).isEqualTo(4L);
    }

    @Test
    void hasEvents_WithExistingEvents_ShouldReturnTrue() {
        // Given
        when(eventRepository.countByAggregateId(AGGREGATE_ID)).thenReturn(3L);

        // When
        boolean result = eventStoreService.hasEvents(AGGREGATE_ID);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void hasEvents_WithNoEvents_ShouldReturnFalse() {
        // Given
        when(eventRepository.countByAggregateId(AGGREGATE_ID)).thenReturn(0L);

        // When
        boolean result = eventStoreService.hasEvents(AGGREGATE_ID);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void getEventsByAggregateType_ShouldReturnEventsInTimeRange() {
        // Given
        OffsetDateTime from = OffsetDateTime.now().minusHours(1);
        OffsetDateTime to = OffsetDateTime.now();
        List<EventEntity> expectedEvents = Arrays.asList(
            createEventEntity(1L, "Event1"),
            createEventEntity(2L, "Event2")
        );

        when(eventRepository.findByAggregateTypeAndTimestampBetweenOrderByTimestamp(
            AGGREGATE_TYPE, from, to)).thenReturn(expectedEvents);

        // When
        List<EventEntity> result = eventStoreService.getEventsByAggregateType(AGGREGATE_TYPE, from, to);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).isEqualTo(expectedEvents);
    }

    @Test
    void getEventsByEventType_ShouldReturnEventsInTimeRange() {
        // Given
        OffsetDateTime from = OffsetDateTime.now().minusHours(1);
        OffsetDateTime to = OffsetDateTime.now();
        List<EventEntity> expectedEvents = Arrays.asList(createEventEntity(1L, EVENT_TYPE));

        when(eventRepository.findByEventTypeAndTimestampBetweenOrderByTimestamp(
            EVENT_TYPE, from, to)).thenReturn(expectedEvents);

        // When
        List<EventEntity> result = eventStoreService.getEventsByEventType(EVENT_TYPE, from, to);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result).isEqualTo(expectedEvents);
    }

    @Test
    void getEventsAfterTimestamp_ShouldReturnEventsAfterGivenTime() {
        // Given
        OffsetDateTime timestamp = OffsetDateTime.now().minusHours(1);
        List<EventEntity> expectedEvents = Arrays.asList(createEventEntity(1L, "Event1"));

        when(eventRepository.findByTimestampGreaterThanOrderByTimestamp(timestamp))
            .thenReturn(expectedEvents);

        // When
        List<EventEntity> result = eventStoreService.getEventsAfterTimestamp(timestamp);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result).isEqualTo(expectedEvents);
    }

    @Test
    void storeEvent_WithUnexpectedException_ShouldThrowEventStoreException() {
        // Given
        Long expectedSequence = 1L;
        Object eventData = new TestEventPayload("test data");

        when(eventRepository.findTopByAggregateIdOrderBySequenceNumberDesc(AGGREGATE_ID))
            .thenReturn(Optional.empty());
        when(objectMapper.valueToTree(eventData))
            .thenThrow(new RuntimeException("Serialization error"));

        // When & Then
        assertThatThrownBy(() -> eventStoreService.storeEvent(AGGREGATE_ID, AGGREGATE_TYPE, 
                                                             expectedSequence, EVENT_TYPE, eventData, null))
            .isInstanceOf(EventStoreException.class)
            .hasMessageContaining("Failed to store event");
    }

    // Helper methods and test classes

    private EventEntity createEventEntity(Long sequenceNumber, String eventType) {
        EventEntity event = new EventEntity(AGGREGATE_ID, AGGREGATE_TYPE, sequenceNumber, 
                                          eventType, mock(JsonNode.class));
        event.setId(sequenceNumber);
        return event;
    }

    private static class TestEventPayload {
        private final String data;

        public TestEventPayload(String data) {
            this.data = data;
        }

        public String getData() {
            return data;
        }
    }

    private static class TestMetadata {
        private final String metadata;

        public TestMetadata(String metadata) {
            this.metadata = metadata;
        }

        public String getMetadata() {
            return metadata;
        }
    }
}