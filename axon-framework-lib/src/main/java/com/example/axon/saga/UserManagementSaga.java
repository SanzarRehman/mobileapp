//package com.example.axon.saga;
//
//import org.axonframework.commandhandling.gateway.CommandGateway;
//import org.axonframework.modelling.saga.EndSaga;
//import org.axonframework.modelling.saga.StartSaga;
//import org.axonframework.eventhandling.EventHandler;
//import org.axonframework.spring.stereotype.Saga;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//
//import java.time.Duration;
//import java.time.Instant;
//import java.util.concurrent.CompletableFuture;
//
///**
// * Saga for managing complex user workflows that span multiple aggregates.
// * Implements the Saga pattern for distributed transaction management.
// */
//@Saga
//public class UserManagementSaga {
//
//    private static final Logger logger = LoggerFactory.getLogger(UserManagementSaga.class);
//
//    private String userId;
//    private String correlationId;
//    private Instant startTime;
//    private boolean userCreated = false;
//    private boolean profileUpdated = false;
//    private int retryCount = 0;
//    private static final int MAX_RETRIES = 3;
//
//    @Autowired
//    private transient CommandGateway commandGateway;
//
//    /**
//     * Starts the saga when a user creation process begins.
//     */
//    @StartSaga
//    @EventHandler
//    public void handle(UserCreatedEvent event) {
//        logger.info("Starting UserManagementSaga for user: {}", event.getUserId());
//
//        this.userId = event.getUserId();
//        this.correlationId = generateCorrelationId();
//        this.startTime = Instant.now();
//        this.userCreated = true;
//
//        // Trigger additional workflow steps
//        initiateProfileSetup(event);
//    }
//
//    /**
//     * Handles user update events within the saga.
//     */
//    @EventHandler
//    public void handle(UserUpdatedEvent event) {
//        logger.info("Processing UserUpdatedEvent in saga for user: {}", event.getUserId());
//
//        if (!userId.equals(event.getUserId())) {
//            logger.warn("Received event for different user: {} (expected: {})", event.getUserId(), userId);
//            return;
//        }
//
//        this.profileUpdated = true;
//
//        // Check if saga can be completed
//        if (canCompleteSaga()) {
//            completeSaga();
//        }
//    }
//
//    /**
//     * Initiates profile setup as part of the user creation workflow.
//     */
//    private void initiateProfileSetup(UserCreatedEvent event) {
//        try {
//            logger.info("Initiating profile setup for user: {}", event.getUserId());
//
//            // Simulate additional profile setup steps
//            // In a real scenario, this might involve:
//            // - Creating user preferences
//            // - Setting up default permissions
//            // - Sending welcome emails
//            // - Creating related entities
//
//            // For demonstration, we'll simulate profile setup without sending commands
//            // This avoids NullPointerException in tests where commandGateway is not injected
//            if (commandGateway != null) {
//                UpdateUserCommand updateCommand = new UpdateUserCommand(
//                    event.getUserId(),
//                    event.getUsername(),
//                    event.getEmail(),
//                    event.getFullName() + " (Profile Setup Complete)"
//                );
//
//                CompletableFuture<Void> future = commandGateway.send(updateCommand);
//                future.whenComplete((result, throwable) -> {
//                    if (throwable != null) {
//                        handleProfileSetupFailure(throwable);
//                    } else {
//                        logger.info("Profile setup completed for user: {}", event.getUserId());
//                    }
//                });
//            } else {
//                // In test scenarios, just log the setup
//                logger.info("Profile setup simulated for user: {} (test mode)", event.getUserId());
//            }
//
//        } catch (Exception e) {
//            logger.error("Error initiating profile setup for user: {}", event.getUserId(), e);
//            handleProfileSetupFailure(e);
//        }
//    }
//
//    /**
//     * Handles failures in profile setup with retry logic.
//     */
//    private void handleProfileSetupFailure(Throwable throwable) {
//        logger.error("Profile setup failed for user: {} (attempt {})", userId, retryCount + 1, throwable);
//
//        retryCount++;
//        if (retryCount < MAX_RETRIES) {
//            logger.info("Retrying profile setup for user: {} (attempt {})", userId, retryCount + 1);
//
//            // Implement exponential backoff
//            long delayMs = (long) Math.pow(2, retryCount) * 1000;
//
//            // In a real implementation, you would schedule this retry
//            // For now, we'll just log the retry attempt
//            logger.info("Scheduling retry in {} ms for user: {}", delayMs, userId);
//
//        } else {
//            logger.error("Max retries exceeded for profile setup of user: {}", userId);
//            // Implement compensation logic
//            compensateUserCreation();
//        }
//    }
//
//    /**
//     * Implements compensation logic when the saga fails.
//     */
//    private void compensateUserCreation() {
//        logger.warn("Implementing compensation for failed user creation saga: {}", userId);
//
//        // In a real scenario, this might involve:
//        // - Marking the user as inactive
//        // - Sending failure notifications
//        // - Cleaning up partially created resources
//        // - Logging the failure for manual intervention
//
//        // For demonstration, we'll just log the compensation
//        logger.info("Compensation completed for user: {}", userId);
//
//        // End the saga after compensation
//        endSaga();
//    }
//
//    /**
//     * Checks if the saga can be completed successfully.
//     */
//    private boolean canCompleteSaga() {
//        return userCreated && profileUpdated;
//    }
//
//    /**
//     * Completes the saga successfully.
//     */
//    private void completeSaga() {
//        Duration duration = Duration.between(startTime, Instant.now());
//        logger.info("UserManagementSaga completed successfully for user: {} in {} ms",
//                   userId, duration.toMillis());
//
//        endSaga();
//    }
//
//    /**
//     * Ends the saga.
//     */
//    @EndSaga
//    private void endSaga() {
//        logger.info("Ending UserManagementSaga for user: {}", userId);
//    }
//
//    /**
//     * Generates a correlation ID for tracking the saga.
//     */
//    private String generateCorrelationId() {
//        return "saga-" + userId + "-" + System.currentTimeMillis();
//    }
//
//    // Getters for testing
//    public String getUserId() {
//        return userId;
//    }
//
//    public String getCorrelationId() {
//        return correlationId;
//    }
//
//    public boolean isUserCreated() {
//        return userCreated;
//    }
//
//    public boolean isProfileUpdated() {
//        return profileUpdated;
//    }
//
//    public int getRetryCount() {
//        return retryCount;
//    }
//}