package com.example.mainapplication.handler;

import com.example.mainapplication.command.CreateUserCommand;
import com.example.mainapplication.command.UpdateUserCommand;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Command handler for user-related commands.
 * This handler processes commands and delegates to the appropriate aggregate.
 */
@Component
public class UserCommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(UserCommandHandler.class);
    private final CommandGateway commandGateway;

    public UserCommandHandler(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    /**
     * Handles CreateUserCommand by delegating to the UserAggregate.
     */
    @CommandHandler
    public CompletableFuture<String> handle(CreateUserCommand command) {
        logger.info("Processing CreateUserCommand for user: {}", command.getUserId());
        
        try {
            return commandGateway.send(command);
        } catch (Exception e) {
            logger.error("Error processing CreateUserCommand for user: {}", command.getUserId(), e);
            throw e;
        }
    }

    /**
     * Handles UpdateUserCommand by delegating to the UserAggregate.
     */
    @CommandHandler
    public CompletableFuture<Void> handle(UpdateUserCommand command) {
        logger.info("Processing UpdateUserCommand for user: {}", command.getUserId());
        
        try {
            return commandGateway.send(command);
        } catch (Exception e) {
            logger.error("Error processing UpdateUserCommand for user: {}", command.getUserId(), e);
            throw e;
        }
    }
}