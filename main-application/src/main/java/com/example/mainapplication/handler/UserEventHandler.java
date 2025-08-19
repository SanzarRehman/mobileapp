package com.example.mainapplication.handler;

import com.example.mainapplication.event.UserCreatedEvent;
import com.example.mainapplication.event.UserUpdatedEvent;
import com.example.mainapplication.projection.UserProjection;
import com.example.mainapplication.repository.UserProjectionRepository;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Event handler for processing user-related events and updating projections.
 * This handler listens to events from Kafka and updates the read-side projections.
 */
@Component
@ProcessingGroup("my-subscribing-group")
public class UserEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(UserEventHandler.class);

    private final UserProjectionRepository userProjectionRepository;

    @Autowired
    public UserEventHandler(UserProjectionRepository userProjectionRepository) {
        this.userProjectionRepository = userProjectionRepository;
    }

    /**
     * Handles UserCreatedEvent by creating a new user projection.
     * 
     * @param event The UserCreatedEvent to process
     */
    @EventHandler
    public void on(UserCreatedEvent event) {
        logger.info("🎉 EVENT HANDLER: Processing UserCreatedEvent for user: {}", event.getUserId());
        logger.info("🎉 EVENT HANDLER: User details - username: {}, email: {}, fullName: {}", 
                   event.getUsername(), event.getEmail(), event.getFullName());
        
        try {
            // Check if projection already exists to avoid duplicates
            if (userProjectionRepository.existsById(event.getUserId())) {
                logger.warn("User projection already exists for user: {}", event.getUserId());
                return;
            }

            UserProjection projection = new UserProjection();
            projection.setId(event.getUserId());
            projection.setUsername(event.getUsername());
            projection.setFullName(event.getFullName());
            projection.setEmail(event.getEmail());
            projection.setStatus("ACTIVE"); // Default status for new users
            projection.setCreatedAt(OffsetDateTime.ofInstant(event.getCreatedAt(), ZoneOffset.UTC));
            projection.setUpdatedAt(OffsetDateTime.ofInstant(event.getCreatedAt(), ZoneOffset.UTC));

            userProjectionRepository.save(projection);
            
            logger.info("🎉 EVENT HANDLER: Successfully processed UserCreatedEvent for user: {}", event.getUserId());
        } catch (Exception e) {
            logger.error("Error processing UserCreatedEvent for user: {}", event.getUserId(), e);
            throw e; // Re-throw to trigger retry mechanism
        }
    }

    /**
     * Handles UserUpdatedEvent by updating the existing user projection.
     * 
     * @param event The UserUpdatedEvent to process
     */
    @EventHandler
    public void on(UserUpdatedEvent event) {
        logger.info("🔄 EVENT HANDLER: Processing UserUpdatedEvent for user: {}", event.getUserId());
        logger.info("🔄 EVENT HANDLER: Updated details - username: {}, email: {}, fullName: {}", 
                   event.getUsername(), event.getEmail(), event.getFullName());
        
        try {
            UserProjection projection = userProjectionRepository.findById(event.getUserId())
                .orElseThrow(() -> new IllegalStateException(
                    "Cannot update non-existent user projection: " + event.getUserId()));

            // Update projection with new data
            projection.setUsername(event.getUsername());
            projection.setFullName(event.getFullName());
            projection.setEmail(event.getEmail());
            projection.setUpdatedAt(OffsetDateTime.ofInstant(event.getUpdatedAt(), ZoneOffset.UTC));

            userProjectionRepository.save(projection);
            
            logger.info("🔄 EVENT HANDLER: Successfully processed UserUpdatedEvent for user: {}", event.getUserId());
        } catch (Exception e) {
            logger.error("Error processing UserUpdatedEvent for user: {}", event.getUserId(), e);
            throw e; // Re-throw to trigger retry mechanism
        }
    }
}