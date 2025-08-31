//package com.example.mainapplication.service;
//
//import com.example.grpc.common.CommandHandlingServiceGrpc;
//import com.example.grpc.common.SubmitCommandRequest;
//import com.example.grpc.common.SubmitCommandResponse;
//import com.example.mainapplication.messaging.DeadLetterQueueHandler;
//import com.example.mainapplication.resilience.CircuitBreakerService;
//import com.example.mainapplication.resilience.RetryService;
//import com.google.protobuf.ByteString;
//import io.grpc.ManagedChannel;
//import io.grpc.ManagedChannelBuilder;
//import org.axonframework.commandhandling.CommandMessage;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.http.*;
//import org.springframework.stereotype.Service;
//import org.springframework.web.client.RestTemplate;
//
//import java.nio.charset.StandardCharsets;
//import java.util.HashMap;
//import java.util.Map;
//
///**
// * Service for routing commands to the custom Axon server.
// */
//@Service
//public class CustomServerCommandService {
//
//    private static final Logger logger = LoggerFactory.getLogger(CustomServerCommandService.class);
//
//    private final RestTemplate restTemplate;
//    private final String customServerUrl;
//    private final CircuitBreakerService circuitBreakerService;
//    private final RetryService retryService;
//    private final DeadLetterQueueHandler deadLetterQueueHandler;
//
//    public CustomServerCommandService(@Qualifier("restTemplate") RestTemplate restTemplate,
//                                    @Value("${app.custom-server.url:http://localhost:8081}") String customServerUrl,
//                                    CircuitBreakerService circuitBreakerService,
//                                    RetryService retryService,
//                                    DeadLetterQueueHandler deadLetterQueueHandler) {
//        this.restTemplate = restTemplate;
//        this.customServerUrl = customServerUrl;
//        this.circuitBreakerService = circuitBreakerService;
//        this.retryService = retryService;
//        this.deadLetterQueueHandler = deadLetterQueueHandler;
//    }
//
//    /**
//     * Routes a command to the custom server with resilience patterns.
//     */
//    public <T> T routeCommand(CommandMessage<?> commandMessage, Class<T> responseType) {
//        String operationName = "routeCommand-" + commandMessage.getCommandName();
//
//        return circuitBreakerService.executeWithCircuitBreaker("custom-server", () ->
//            retryService.executeWithRetry(operationName, () -> {
//                try {
//                    String url = customServerUrl + "/api/commands/submit";
//
//
//                    ManagedChannel channel = ManagedChannelBuilder
//                        .forAddress("localhost", 9060)
//                        .usePlaintext()
//                        .build();
//
//                    CommandHandlingServiceGrpc.CommandHandlingServiceBlockingStub stub =
//                        CommandHandlingServiceGrpc.newBlockingStub(channel);
//
//
//
//                    // Extract aggregateId from the command payload
//                    String aggregateId = extractAggregateId(commandMessage);
//
//                    // Create request payload
//                    Map<String, Object> payload = new HashMap<>();
//                    payload.put("commandType", commandMessage.getCommandName());
//                    payload.put("commandId", commandMessage.getIdentifier());
//                    payload.put("aggregateId", aggregateId);  // Add the missing aggregateId
//                    payload.put("payload", commandMessage.getPayload());
//                    payload.put("metadata", commandMessage.getMetaData());
//
//                    // Set headers
//                    SubmitCommandRequest request2 = SubmitCommandRequest.newBuilder()
//                        .setCommandId("cmd-123")
//                        .setAggregateId("order-42")
//                        .setPayload(ByteString.copyFrom(payload.toString().getBytes(StandardCharsets.UTF_8)))
//                        .setCommandType(commandMessage.getCommandName())
//                        .build();
//
//                    SubmitCommandResponse response2 = stub.submitCommand(request2);
//
//                    System.out.println("✅ Got response: " + response2.getMessage());
//                    channel.shutdown();
//
////                    ResponseEntity<T> response = restTemplate.exchange(
////                            url,
////                            HttpMethod.POST,
////                            request,
////                            responseType
////                    );
//
////                    if (response.getStatusCode().is2xxSuccessful()) {
////                        logger.debug("Command {} routed successfully", commandMessage.getCommandName());
////                        return response.getBody();
////                    } else {
////                        throw new RuntimeException("Failed to route command: " + response.getStatusCode());
////                    }
//                    if (1==1) {
//                        logger.debug("Command {} routed successfully", commandMessage.getCommandName());
//                        return null;
//                    } else {
//                        throw new RuntimeException("Failed to route command: ");
//                    }
//                } catch (Exception e) {
//                    logger.error("Error routing command {} to custom server",
//                            commandMessage.getCommandName(), e);
//
//                    // Send to dead letter queue if all retries fail
//                    if (isNonRetryableError(e)) {
//                        deadLetterQueueHandler.sendToDeadLetterQueue("commands", commandMessage, e);
//                    }
//
//                    throw new RuntimeException("Failed to route command to custom server", e);
//                }
//            })
//        );
//    }
//
//    /**
//     * Check if custom server is available with circuit breaker.
//     */
//    public boolean isCustomServerAvailable() {
//        try {
//            return circuitBreakerService.executeWithCircuitBreaker("custom-server-health", () -> {
//                String healthUrl = customServerUrl + "/actuator/health";
//                ResponseEntity<String> response = restTemplate.getForEntity(healthUrl, String.class);
//                return response.getStatusCode().is2xxSuccessful();
//            });
//        } catch (Exception e) {
//            logger.warn("Custom server is not available: {}", e.getMessage());
//            return false;
//        }
//    }
//
//    private boolean isNonRetryableError(Exception e) {
//        // Determine if the error should not be retried (e.g., validation errors)
//        return e instanceof IllegalArgumentException ||
//               (e.getMessage() != null && e.getMessage().contains("400"));
//    }
//
//    /**
//     * Extracts the aggregate ID from the command payload.
//     * This method looks for common aggregate ID fields in the command.
//     */
//    private String extractAggregateId(CommandMessage<?> commandMessage) {
//        Object command = commandMessage.getPayload();
//
//        // Handle CreateUserCommand and UpdateUserCommand
//        if (command instanceof com.example.mainapplication.command.CreateUserCommand) {
//            return ((com.example.mainapplication.command.CreateUserCommand) command).getUserId();
//        }
//        if (command instanceof com.example.mainapplication.command.UpdateUserCommand) {
//            return ((com.example.mainapplication.command.UpdateUserCommand) command).getUserId();
//        }
//
//        // Fallback: try to extract from command using reflection
//        try {
//            // Look for common aggregate ID field names
//            String[] possibleFields = {"userId", "id", "aggregateId"};
//
//            for (String fieldName : possibleFields) {
//                try {
//                    java.lang.reflect.Field field = command.getClass().getDeclaredField(fieldName);
//                    field.setAccessible(true);
//                    Object value = field.get(command);
//                    if (value != null) {
//                        return value.toString();
//                    }
//                } catch (NoSuchFieldException | IllegalAccessException e) {
//                    // Continue to next field
//                }
//            }
//
//            // If no field found, use command identifier as fallback
//            logger.warn("Could not extract aggregateId from command {}, using command identifier",
//                       commandMessage.getCommandName());
//            return commandMessage.getIdentifier();
//
//        } catch (Exception e) {
//            logger.error("Error extracting aggregateId from command {}: {}",
//                        commandMessage.getCommandName(), e.getMessage());
//            return commandMessage.getIdentifier();
//        }
//    }
//}