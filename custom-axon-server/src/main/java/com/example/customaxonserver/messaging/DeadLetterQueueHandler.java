//package com.example.customaxonserver.messaging;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.kafka.annotation.KafkaListener;
//import org.springframework.kafka.core.KafkaTemplate;
//import org.springframework.kafka.support.KafkaHeaders;
//import org.springframework.messaging.handler.annotation.Header;
//import org.springframework.messaging.handler.annotation.Payload;
//import org.springframework.stereotype.Service;
//
//import java.time.LocalDateTime;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.atomic.AtomicInteger;
//
//@Service
//public class DeadLetterQueueHandler {
//
//    private static final Logger logger = LoggerFactory.getLogger(DeadLetterQueueHandler.class);
//    private static final String DLQ_TOPIC_SUFFIX = ".dlq";
//    private static final int MAX_RETRY_ATTEMPTS = 3;
//
//    @Autowired
//    private KafkaTemplate<String, Object> kafkaTemplate;
//
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    private final Map<String, AtomicInteger> retryCounters = new ConcurrentHashMap<>();
//
//    public void sendToDeadLetterQueue(String originalTopic, Object message, Exception exception) {
//        try {
//            String dlqTopic = originalTopic + DLQ_TOPIC_SUFFIX;
//
//            Map<String, Object> dlqMessage = new HashMap<>();
//            dlqMessage.put("originalTopic", originalTopic);
//            dlqMessage.put("originalMessage", message);
//            dlqMessage.put("errorMessage", exception.getMessage());
//            dlqMessage.put("errorClass", exception.getClass().getSimpleName());
//            dlqMessage.put("timestamp", LocalDateTime.now().toString());
//            dlqMessage.put("stackTrace", getStackTrace(exception));
//
//            kafkaTemplate.send(dlqTopic, dlqMessage);
//
//            logger.error("Message sent to dead letter queue. Topic: {}, Error: {}",
//                    dlqTopic, exception.getMessage(), exception);
//
//        } catch (Exception e) {
//            logger.error("Failed to send message to dead letter queue", e);
//        }
//    }
//
//    @KafkaListener(topics = "event-store.dlq")
//    public void handleEventStoreDlq(@Payload String message,
//                                   @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
//        logger.info("Processing message from event store DLQ: {}", topic);
//
//        try {
//            Map<String, Object> dlqMessage = objectMapper.readValue(message, Map.class);
//            String originalTopic = (String) dlqMessage.get("originalTopic");
//            String messageKey = generateMessageKey(dlqMessage);
//
//            AtomicInteger retryCount = retryCounters.computeIfAbsent(messageKey, k -> new AtomicInteger(0));
//
//            if (retryCount.incrementAndGet() <= MAX_RETRY_ATTEMPTS) {
//                logger.info("Retrying event store message from DLQ, attempt: {}", retryCount.get());
//
//                // Attempt to reprocess the message
//                Object originalMessage = dlqMessage.get("originalMessage");
//                kafkaTemplate.send(originalTopic, originalMessage);
//
//            } else {
//                logger.error("Event store message exceeded maximum retry attempts: {}", messageKey);
//                storePermanentFailure(dlqMessage);
//                retryCounters.remove(messageKey);
//            }
//
//        } catch (Exception e) {
//            logger.error("Failed to process event store DLQ message", e);
//        }
//    }
//
//    @KafkaListener(topics = "command-routing.dlq")
//    public void handleCommandRoutingDlq(@Payload String message,
//                                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
//        logger.info("Processing message from command routing DLQ: {}", topic);
//
//        try {
//            Map<String, Object> dlqMessage = objectMapper.readValue(message, Map.class);
//            String messageKey = generateMessageKey(dlqMessage);
//
//            AtomicInteger retryCount = retryCounters.computeIfAbsent(messageKey, k -> new AtomicInteger(0));
//
//            if (retryCount.incrementAndGet() <= MAX_RETRY_ATTEMPTS) {
//                logger.info("Retrying command routing from DLQ, attempt: {}", retryCount.get());
//
//                // Commands require careful handling - log for manual intervention
//                logger.warn("Command routing failure requires manual intervention: {}", dlqMessage);
//
//            } else {
//                logger.error("Command routing exceeded maximum retry attempts: {}", messageKey);
//                storePermanentFailure(dlqMessage);
//                retryCounters.remove(messageKey);
//            }
//
//        } catch (Exception e) {
//            logger.error("Failed to process command routing DLQ message", e);
//        }
//    }
//
//    @KafkaListener(topics = "query-routing.dlq")
//    public void handleQueryRoutingDlq(@Payload String message,
//                                     @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
//        logger.info("Processing message from query routing DLQ: {}", topic);
//
//        try {
//            Map<String, Object> dlqMessage = objectMapper.readValue(message, Map.class);
//            String originalTopic = (String) dlqMessage.get("originalTopic");
//            String messageKey = generateMessageKey(dlqMessage);
//
//            AtomicInteger retryCount = retryCounters.computeIfAbsent(messageKey, k -> new AtomicInteger(0));
//
//            if (retryCount.incrementAndGet() <= MAX_RETRY_ATTEMPTS) {
//                logger.info("Retrying query routing from DLQ, attempt: {}", retryCount.get());
//
//                // Attempt to reprocess the query
//                Object originalMessage = dlqMessage.get("originalMessage");
//                kafkaTemplate.send(originalTopic, originalMessage);
//
//            } else {
//                logger.error("Query routing exceeded maximum retry attempts: {}", messageKey);
//                storePermanentFailure(dlqMessage);
//                retryCounters.remove(messageKey);
//            }
//
//        } catch (Exception e) {
//            logger.error("Failed to process query routing DLQ message", e);
//        }
//    }
//
//    private String generateMessageKey(Map<String, Object> dlqMessage) {
//        String originalTopic = (String) dlqMessage.get("originalTopic");
//        String timestamp = (String) dlqMessage.get("timestamp");
//        return originalTopic + "_" + timestamp;
//    }
//
//    private void storePermanentFailure(Map<String, Object> dlqMessage) {
//        // In a real implementation, this would store to a database or persistent storage
//        // For now, we'll just log it
//        logger.error("PERMANENT FAILURE - Message could not be processed: {}", dlqMessage);
//
//        // Could implement:
//        // - Database storage for failed messages
//        // - Notification to administrators
//        // - Metrics collection for failure analysis
//    }
//
//    private String getStackTrace(Exception exception) {
//        StringBuilder sb = new StringBuilder();
//        for (StackTraceElement element : exception.getStackTrace()) {
//            sb.append(element.toString()).append("\n");
//        }
//        return sb.toString();
//    }
//}