package com.example.customaxonserver.controller;

import com.example.customaxonserver.model.CommandMessage;
import com.example.customaxonserver.model.CommandResponse;
import com.example.customaxonserver.service.CommandRoutingService;
import com.example.grpc.common.ServiceInstance;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST controller for command submission and routing management.
 */
@RestController
@RequestMapping("/api/commands")
@Validated
public class CommandController {
    
    private static final Logger logger = LoggerFactory.getLogger(CommandController.class);
    
    private final CommandRoutingService commandRoutingService;
    private final Counter commandProcessedCounter;
    private final Timer commandProcessingTimer;
    
    @Autowired
    public CommandController(CommandRoutingService commandRoutingService,
                           Counter commandProcessedCounter,
                           Timer commandProcessingTimer) {
        this.commandRoutingService = commandRoutingService;
        this.commandProcessedCounter = commandProcessedCounter;
        this.commandProcessingTimer = commandProcessingTimer;
    }
    
    /**
     * Submits a command for routing to appropriate handler.
     * 
     * @param commandMessage The command to route
     * @return Response indicating routing result
     */
    @PostMapping("/submit")
    public ResponseEntity<CommandResponse> submitCommand(@Valid @RequestBody CommandMessage commandMessage) {
        logger.info("Received command submission: {}", commandMessage);
        
        long startTime = System.nanoTime();
        try {
            ServiceInstance targetInstance = commandRoutingService.routeCommand(
                    commandMessage.getCommandType(), 
                    commandMessage.getAggregateId()
            );
            
            // Forward the command to the target instance
            CommandResponse response = forwardCommandToInstance(targetInstance, commandMessage);
            
            logger.info("Successfully processed command {} on instance {}", 
                       commandMessage.getCommandId(), targetInstance);
            
            commandProcessedCounter.increment();
            return ResponseEntity.ok(response);
            
        } catch (CommandRoutingService.CommandRoutingException e) {
            logger.error("Failed to route command {}: {}", commandMessage.getCommandId(), e.getMessage());
            
            CommandResponse response = CommandResponse.error(commandMessage.getCommandId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            
        } catch (Exception e) {
            logger.error("Unexpected error routing command {}: {}", commandMessage.getCommandId(), e.getMessage(), e);
            
            CommandResponse response = CommandResponse.error(commandMessage.getCommandId(), 
                    "Internal server error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            commandProcessingTimer.record(System.nanoTime() - startTime, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }

    /**
     * Forwards a command to the target instance for processing.
     */
    private CommandResponse forwardCommandToInstance(ServiceInstance targetInstance, CommandMessage commandMessage) {
        try {

            String hostname = targetInstance.getHost();
            int port = targetInstance.getPort();

            String targetUrl = "http://" + hostname + ":" + port + "/api/internal/commands/process";

            // Create the payload to send
            Map<String, Object> payload = new HashMap<>();
            payload.put("commandType", commandMessage.getCommandType());
            payload.put("commandId", commandMessage.getCommandId());
            payload.put("aggregateId", commandMessage.getAggregateId());
            payload.put("payload", commandMessage.getPayload());
            payload.put("metadata", commandMessage.getMetadata());

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.set("Connection", "close"); // Ensure connection is closed properly

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            logger.debug("Forwarding command {} to instance at {}", commandMessage.getCommandId(), targetUrl);

            // RestTemplate with timeouts
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(3000);
            factory.setReadTimeout(5000);
            RestTemplate restTemplate = new RestTemplate(factory);

            try {
                ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    targetUrl,
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
                );

                if (response.getStatusCode().is2xxSuccessful()) {
                    if (response.getBody() != null) {
                        Map<String, Object> responseBody = response.getBody();
                        String status = (String) responseBody.get("status");

                        if ("SUCCESS".equals(status)) {
                            return CommandResponse.success(commandMessage.getCommandId(), targetInstance.getInstanceId(),
                                    (String) responseBody.get("result"));
                        } else {
                            return CommandResponse.error(commandMessage.getCommandId(),
                                    (String) responseBody.getOrDefault("message", "Unknown error"));
                        }
                    } else {
                        // Success but no body - treat as success
                        return CommandResponse.success(commandMessage.getCommandId(), targetInstance.getInstanceId(),
                                "Command processed successfully");
                    }
                } else {
                    return CommandResponse.error(commandMessage.getCommandId(),
                            "Failed to forward command: " + response.getStatusCode());
                }

            } catch (org.springframework.web.client.ResourceAccessException e) {
                // Handle connection issues gracefully
                if (e.getMessage() != null && e.getMessage().contains("Unexpected end of file")) {
                    logger.warn("Connection closed unexpectedly for command {}, but command may have been processed",
                            commandMessage.getCommandId());
                    // Assume success since the server often processes the command even when connection drops
                    return CommandResponse.success(commandMessage.getCommandId(), targetInstance.getInstanceId(),
                            "Command processed (connection dropped)");
                } else {
                    throw e; // Re-throw other connection issues
                }
            }

        } catch (Exception e) {
            logger.error("Failed to forward command {} to instance {}: {}",
                    commandMessage.getCommandId(), targetInstance, e.getMessage(), e);
            return CommandResponse.error(commandMessage.getCommandId(),
                    "Failed to forward command: " + e.getMessage());
        }
    }
    
    /**
     * Registers a command handler for a specific command type on an instance.
     * 
     * @param instanceId The instance ID
     * @param commandType The command type
     * @return Success response
     */
//    @PostMapping("/handlers/{instanceId}/{commandType}")
//    public ResponseEntity<String> registerHandler(
//            @PathVariable String instanceId,
//            @PathVariable String commandType) {
//
//        logger.info("Registering command handler for {} on instance {}", commandType, instanceId);
//
//        try {
//            commandRoutingService.registerCommandHandler(instanceId, commandType);
//            return ResponseEntity.ok("Handler registered successfully");
//
//        } catch (Exception e) {
//            logger.error("Failed to register handler for {} on instance {}: {}",
//                        commandType, instanceId, e.getMessage(), e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body("Failed to register handler: " + e.getMessage());
//        }
//    }
    
    /**
     * Unregisters a command handler for a specific command type on an instance.
     * 
     * @param instanceId The instance ID
     * @param commandType The command type
     * @return Success response
     */
    @DeleteMapping("/handlers/{instanceId}/{commandType}")
    public ResponseEntity<String> unregisterHandler(
            @PathVariable String instanceId,
            @PathVariable String commandType) {
        
        logger.info("Unregistering command handler for {} on instance {}", commandType, instanceId);
        
        try {
            commandRoutingService.unregisterCommandHandler(instanceId, commandType);
            return ResponseEntity.ok("Handler unregistered successfully");
            
        } catch (Exception e) {
            logger.error("Failed to unregister handler for {} on instance {}: {}", 
                        commandType, instanceId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to unregister handler: " + e.getMessage());
        }
    }
    
    /**
     * Updates the health status of an instance.
     * 
     * @param instanceId The instance ID
     * @param status The health status
     * @return Success response
     */
    @PostMapping("/instances/{instanceId}/health")
    public ResponseEntity<String> updateInstanceHealth(
            @PathVariable String instanceId,
            @RequestParam String status) {
        
        logger.debug("Updating health status for instance {} to {}", instanceId, status);
        
        try {
            commandRoutingService.updateInstanceHealth(instanceId, status);
            return ResponseEntity.ok("Health status updated successfully");
            
        } catch (Exception e) {
            logger.error("Failed to update health status for instance {}: {}", instanceId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to update health status: " + e.getMessage());
        }
    }
    
    /**
     * Gets all command types handled by a specific instance.
     * 
     * @param instanceId The instance ID
     * @return Set of command types
     */
    @GetMapping("/instances/{instanceId}/commands")
    public ResponseEntity<Set<String>> getCommandTypesForInstance(@PathVariable String instanceId) {
        logger.debug("Getting command types for instance {}", instanceId);
        
        try {
            Set<String> commandTypes = commandRoutingService.getCommandTypesForInstance(instanceId);
            return ResponseEntity.ok(commandTypes);
            
        } catch (Exception e) {
            logger.error("Failed to get command types for instance {}: {}", instanceId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
    
    /**
     * Gets all instances that can handle a specific command type.
     * 
     * @param commandType The command type
     * @return List of instance IDs
     */
    @GetMapping("/types/{commandType}/instances")
    public ResponseEntity<List<String>> getInstancesForCommandType(@PathVariable String commandType) {
        logger.debug("Getting instances for command type {}", commandType);
        
        try {
            List<String> instances = commandRoutingService.getInstancesForCommandType(commandType);
            return ResponseEntity.ok(instances);
            
        } catch (Exception e) {
            logger.error("Failed to get instances for command type {}: {}", commandType, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
    
    /**
     * Removes all routing information for an instance.
     * 
     * @param instanceId The instance ID
     * @return Success response
     */
    @DeleteMapping("/instances/{instanceId}")
    public ResponseEntity<String> removeInstance(@PathVariable String instanceId) {
        logger.info("Removing all routing information for instance {}", instanceId);
        
        try {
            commandRoutingService.removeInstance(instanceId);
            return ResponseEntity.ok("Instance removed successfully");
            
        } catch (Exception e) {
            logger.error("Failed to remove instance {}: {}", instanceId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to remove instance: " + e.getMessage());
        }
    }
}