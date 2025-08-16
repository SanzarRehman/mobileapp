package com.example.mainapplication.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeadLetterQueueHandlerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private DeadLetterQueueHandler deadLetterQueueHandler;

    @BeforeEach
    void setUp() {
        deadLetterQueueHandler = new DeadLetterQueueHandler();
        // Use reflection to set private fields for testing
        try {
            java.lang.reflect.Field kafkaField = DeadLetterQueueHandler.class.getDeclaredField("kafkaTemplate");
            kafkaField.setAccessible(true);
            kafkaField.set(deadLetterQueueHandler, kafkaTemplate);
            
            java.lang.reflect.Field mapperField = DeadLetterQueueHandler.class.getDeclaredField("objectMapper");
            mapperField.setAccessible(true);
            mapperField.set(deadLetterQueueHandler, objectMapper);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set up test", e);
        }
    }

    @Test
    void sendToDeadLetterQueue_ShouldSendMessageToDlqTopic() {
        // Given
        String originalTopic = "events";
        String testMessage = "test message";
        Exception testException = new RuntimeException("Test error");

        // When
        deadLetterQueueHandler.sendToDeadLetterQueue(originalTopic, testMessage, testException);

        // Then
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> messageCaptor = ArgumentCaptor.forClass(Map.class);
        
        verify(kafkaTemplate).send(topicCaptor.capture(), messageCaptor.capture());
        
        assertEquals("events.dlq", topicCaptor.getValue());
        
        Map<String, Object> dlqMessage = messageCaptor.getValue();
        assertEquals(originalTopic, dlqMessage.get("originalTopic"));
        assertEquals(testMessage, dlqMessage.get("originalMessage"));
        assertEquals("Test error", dlqMessage.get("errorMessage"));
        assertEquals("RuntimeException", dlqMessage.get("errorClass"));
        assertNotNull(dlqMessage.get("timestamp"));
        assertNotNull(dlqMessage.get("stackTrace"));
    }

    @Test
    void sendToDeadLetterQueue_KafkaFailure_ShouldHandleGracefully() {
        // Given
        String originalTopic = "events";
        String testMessage = "test message";
        Exception testException = new RuntimeException("Test error");
        
        doThrow(new RuntimeException("Kafka failure")).when(kafkaTemplate).send(any(String.class), any());

        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> 
            deadLetterQueueHandler.sendToDeadLetterQueue(originalTopic, testMessage, testException)
        );
    }

    @Test
    void handleEventsDlq_ValidMessage_ShouldProcessSuccessfully() throws Exception {
        // Given
        String dlqMessage = "{\"originalTopic\":\"events\",\"originalMessage\":\"test\",\"timestamp\":\"2024-01-01T10:00:00\"}";
        String topic = "events.dlq";
        
        Map<String, Object> parsedMessage = Map.of(
            "originalTopic", "events",
            "originalMessage", "test",
            "timestamp", "2024-01-01T10:00:00"
        );
        
        when(objectMapper.readValue(dlqMessage, Map.class)).thenReturn(parsedMessage);

        // When
        deadLetterQueueHandler.handleEventsDlq(dlqMessage, topic);

        // Then
        verify(objectMapper).readValue(dlqMessage, Map.class);
        verify(kafkaTemplate).send(eq("events"), eq("test"));
    }

    @Test
    void handleEventsDlq_InvalidMessage_ShouldHandleGracefully() throws Exception {
        // Given
        String invalidMessage = "invalid json";
        String topic = "events.dlq";
        
        when(objectMapper.readValue(invalidMessage, Map.class))
            .thenThrow(new RuntimeException("JSON parsing error"));

        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> 
            deadLetterQueueHandler.handleEventsDlq(invalidMessage, topic)
        );
        
        verify(kafkaTemplate, never()).send(any(String.class), any());
    }

    @Test
    void handleCommandsDlq_ValidMessage_ShouldLogForManualIntervention() throws Exception {
        // Given
        String dlqMessage = "{\"originalTopic\":\"commands\",\"originalMessage\":\"test\",\"timestamp\":\"2024-01-01T10:00:00\"}";
        String topic = "commands.dlq";
        
        Map<String, Object> parsedMessage = Map.of(
            "originalTopic", "commands",
            "originalMessage", "test",
            "timestamp", "2024-01-01T10:00:00"
        );
        
        when(objectMapper.readValue(dlqMessage, Map.class)).thenReturn(parsedMessage);

        // When
        deadLetterQueueHandler.handleCommandsDlq(dlqMessage, topic);

        // Then
        verify(objectMapper).readValue(dlqMessage, Map.class);
        // Commands should not be automatically retried, so no kafka send should occur
        verify(kafkaTemplate, never()).send(any(String.class), any());
    }

    @Test
    void handleEventsDlq_ExceedsMaxRetries_ShouldStorePermanentFailure() throws Exception {
        // Given
        String dlqMessage = "{\"originalTopic\":\"events\",\"originalMessage\":\"test\",\"timestamp\":\"2024-01-01T10:00:00\"}";
        String topic = "events.dlq";
        
        Map<String, Object> parsedMessage = Map.of(
            "originalTopic", "events",
            "originalMessage", "test",
            "timestamp", "2024-01-01T10:00:00"
        );
        
        when(objectMapper.readValue(dlqMessage, Map.class)).thenReturn(parsedMessage);

        // When - Process the same message multiple times to exceed retry limit
        for (int i = 0; i < 4; i++) { // Process 4 times: 3 retries + 1 permanent failure
            deadLetterQueueHandler.handleEventsDlq(dlqMessage, topic);
        }

        // Then - Should have attempted retries but eventually stopped
        // First 3 calls will retry (attempts 1, 2, 3), 4th call will exceed limit
        verify(kafkaTemplate, times(3)).send(eq("events"), eq("test"));
    }
}