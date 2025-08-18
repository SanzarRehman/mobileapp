package com.example.mainapplication.controller;

import com.example.mainapplication.command.CreateUserCommand;
import com.example.mainapplication.command.UpdateUserCommand;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Controller for receiving commands from the custom axon server.
 * This endpoint allows the custom server to forward commands back to this instance.
 */
@RestController
@RequestMapping("/api/internal/commands")
public class CommandReceiverController {

    private static final Logger logger = LoggerFactory.getLogger(CommandReceiverController.class);
    private final CommandGateway commandGateway;

    public CommandReceiverController(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    /**
     * Receives and processes commands forwarded from the custom axon server.
     */
    @PostMapping("/process")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> processCommand(
            @RequestBody Map<String, Object> commandPayload) {
        
        logger.info("🔍 CommandReceiverController: Received command for processing");
        logger.info("🔍 CommandReceiverController: Full commandPayload: {}", commandPayload);
        logger.info("🔍 CommandReceiverController: CommandPayload keys: {}", commandPayload.keySet());
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String commandType = (String) commandPayload.get("commandType");
                logger.info("🔍 CommandReceiverController: Processing command type: {}", commandType);
                
                Object commandMessage = null;
                
                // Handle both simple class name and fully qualified class name
                if ("CreateUserCommand".equals(commandType) || 
                    "com.example.mainapplication.command.CreateUserCommand".equals(commandType)) {
                    
                    logger.info("🔍 CommandReceiverController: Creating CreateUserCommand");
                    
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = (Map<String, Object>) commandPayload.get("payload");
                    
                    String userId = (String) payload.get("userId");
                    String username = (String) payload.get("username");
                    String email = (String) payload.get("email");
                    String fullName = (String) payload.get("fullName");
                    
                    logger.info("🔍 CommandReceiverController: Command parameters - userId: {}, username: {}, email: {}, fullName: {}", 
                               userId, username, email, fullName);
                    
                    commandMessage = new CreateUserCommand(userId, username, email, fullName);
                    logger.info("🔍 CommandReceiverController: Created CreateUserCommand: {}", commandMessage);
                    
                } else if ("UpdateUserCommand".equals(commandType) || 
                          "com.example.mainapplication.command.UpdateUserCommand".equals(commandType)) {
                    
                    logger.info("🔍 CommandReceiverController: Creating UpdateUserCommand");
                    
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = (Map<String, Object>) commandPayload.get("payload");
                    
                    String userId = (String) payload.get("userId");
                    String username = (String) payload.get("username");
                    String email = (String) payload.get("email");
                    String fullName = (String) payload.get("fullName");
                    
                    commandMessage = new UpdateUserCommand(userId, username, email, fullName);
                    logger.info("🔍 CommandReceiverController: Created UpdateUserCommand: {}", commandMessage);
                    
                } else {
                    logger.error("🔍 CommandReceiverController: Unknown command type: {}", commandType);
                    return ResponseEntity.badRequest().body(Map.of(
                        "status", "ERROR",
                        "message", "Unknown command type: " + commandType
                    ));
                }
                
                logger.info("🔍 CommandReceiverController: About to call commandGateway.sendAndWait()");
                logger.info("🔍 CommandReceiverController: Command object class: {}", commandMessage.getClass().getName());
                logger.info("🔍 CommandReceiverController: Command object details: {}", commandMessage);
                logger.info("🔍 CommandReceiverController: CommandGateway class: {}", commandGateway.getClass().getName());
                
                // Send command and wait for result
                Object result = commandGateway.sendAndWait(commandMessage);
                
                logger.info("🔍 CommandReceiverController: ✅ Successfully executed command!");
                logger.info("🔍 CommandReceiverController: Result: {}", result);
                logger.info("🔍 CommandReceiverController: Result class: {}", result != null ? result.getClass().getName() : "null");
                
                return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Command processed successfully",
                    "result", result != null ? result.toString() : "null"
                ));
                
            } catch (Exception e) {
                logger.error("🔍 CommandReceiverController: ❌ Error processing command: {}", e.getMessage(), e);
                logger.error("🔍 CommandReceiverController: Exception class: {}", e.getClass().getName());
                logger.error("🔍 CommandReceiverController: Exception cause: {}", e.getCause());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of(
                            "status", "ERROR",
                            "message", "Failed to process command: " + e.getMessage(),
                            "exceptionType", e.getClass().getSimpleName()
                        ));
            }
        });
    }

    /**
     * Health check endpoint for the custom server to verify this instance is available.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "timestamp", System.currentTimeMillis()
        ));
    }
}
