package com.example.mainapplication.controller;

import com.example.mainapplication.command.CreateUserCommand;
import com.example.mainapplication.command.UpdateUserCommand;
import com.example.mainapplication.dto.CreateUserRequest;
import com.example.mainapplication.dto.UpdateUserRequest;
import com.example.mainapplication.dto.CommandResponse;
import com.example.mainapplication.service.CustomServerCommandService;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for user command operations.
 * Provides endpoints for creating and updating users.
 */
@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserCommandController {

    private static final Logger logger = LoggerFactory.getLogger(UserCommandController.class);
    private final CustomServerCommandService customServerCommandService;

    public UserCommandController(CustomServerCommandService customServerCommandService) {
        this.customServerCommandService = customServerCommandService;
    }

    /**
     * Create a new user.
     */
    @PostMapping
    public CompletableFuture<ResponseEntity<CommandResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        
        logger.info("Received create user request for username: {}", request.getUsername());
        
        String userId = UUID.randomUUID().toString();
        CreateUserCommand command = new CreateUserCommand(
                userId,
                request.getUsername(),
                request.getEmail(),
                request.getFullName()
        );

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Route command to custom server
                var commandMessage = GenericCommandMessage.asCommandMessage(command);
                customServerCommandService.routeCommand(commandMessage, String.class);
                
                logger.info("User created successfully with ID: {}", userId);
                CommandResponse response = new CommandResponse(
                        userId,
                        "User created successfully",
                        true
                );
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
                
            } catch (Exception e) {
                logger.error("Error creating user: {}", e.getMessage(), e);
                CommandResponse response = new CommandResponse(
                        userId,
                        "Error creating user: " + e.getMessage(),
                        false
                );
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
        });
    }

    /**
     * Update an existing user.
     */
    @PutMapping("/{userId}")
    public CompletableFuture<ResponseEntity<CommandResponse>> updateUser(
            @PathVariable("userId") String userId,
            @Valid @RequestBody UpdateUserRequest request) {
        
        logger.info("Received update user request for user ID: {}", userId);
        
        UpdateUserCommand command = new UpdateUserCommand(
                userId,
                request.getUsername(),
                request.getEmail(),
                request.getFullName()
        );

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Route command to custom server
                var commandMessage = GenericCommandMessage.asCommandMessage(command);
                customServerCommandService.routeCommand(commandMessage, String.class);
                
                logger.info("User updated successfully with ID: {}", userId);
                CommandResponse response = new CommandResponse(
                        userId,
                        "User updated successfully",
                        true
                );
                return ResponseEntity.ok(response);
                
            } catch (Exception e) {
                logger.error("Error updating user: {}", e.getMessage(), e);
                CommandResponse response = new CommandResponse(
                        userId,
                        "Error updating user: " + e.getMessage(),
                        false
                );
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
        });
    }
}