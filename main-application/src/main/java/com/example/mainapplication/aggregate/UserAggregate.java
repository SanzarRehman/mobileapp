package com.example.mainapplication.aggregate;

import com.example.mainapplication.command.CreateUserCommand;
import com.example.mainapplication.command.UpdateUserCommand;
import com.example.mainapplication.event.UserCreatedEvent;
import com.example.mainapplication.event.UserUpdatedEvent;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import java.time.Instant;
import java.util.Objects;

/**
 * User aggregate that handles user-related commands and maintains user state.
 * This aggregate follows the Event Sourcing pattern where state changes are
 * represented as events.
 */
@Aggregate
public class UserAggregate {

    @AggregateIdentifier
    private String userId;
    private String username;
    private String email;
    private String fullName;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    // Required no-arg constructor for Axon Framework
    protected UserAggregate() {
    }

    /**
     * Command handler for creating a new user.
     * Validates business rules and publishes UserCreatedEvent.
     */
    @CommandHandler
    public UserAggregate(CreateUserCommand command) {
        // Business rule validation
        validateUsername(command.getUsername());
        validateEmail(command.getEmail());
        validateFullName(command.getFullName());

        // Apply the event
        Instant now = Instant.now();
        AggregateLifecycle.apply(new UserCreatedEvent(
                command.getUserId(),
                command.getUsername(),
                command.getEmail(),
                command.getFullName(),
                now
        ));
    }

    /**
     * Command handler for updating an existing user.
     * Validates business rules and publishes UserUpdatedEvent.
     */
    @CommandHandler
    public void handle(UpdateUserCommand command) {
        // Business rule validation
        if (!this.active) {
            throw new IllegalStateException("Cannot update inactive user: " + command.getUserId());
        }
        
        validateUsername(command.getUsername());
        validateEmail(command.getEmail());
        validateFullName(command.getFullName());

        // Check if there are actual changes
        if (Objects.equals(this.username, command.getUsername()) &&
            Objects.equals(this.email, command.getEmail()) &&
            Objects.equals(this.fullName, command.getFullName())) {
            // No changes, don't publish event
            return;
        }

        // Apply the event
        Instant now = Instant.now();
        AggregateLifecycle.apply(new UserUpdatedEvent(
                command.getUserId(),
                command.getUsername(),
                command.getEmail(),
                command.getFullName(),
                now
        ));
    }

    /**
     * Event sourcing handler for UserCreatedEvent.
     * Updates the aggregate state based on the event.
     */
    @EventSourcingHandler
    public void on(UserCreatedEvent event) {
        this.userId = event.getUserId();
        this.username = event.getUsername();
        this.email = event.getEmail();
        this.fullName = event.getFullName();
        this.active = true;
        this.createdAt = event.getCreatedAt();
        this.updatedAt = event.getCreatedAt();
    }

    /**
     * Event sourcing handler for UserUpdatedEvent.
     * Updates the aggregate state based on the event.
     */
    @EventSourcingHandler
    public void on(UserUpdatedEvent event) {
        this.username = event.getUsername();
        this.email = event.getEmail();
        this.fullName = event.getFullName();
        this.updatedAt = event.getUpdatedAt();
    }

    // Business rule validation methods
    private void validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        if (username.length() < 3) {
            throw new IllegalArgumentException("Username must be at least 3 characters long");
        }
        if (username.length() > 50) {
            throw new IllegalArgumentException("Username cannot exceed 50 characters");
        }
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException("Username can only contain letters, numbers, and underscores");
        }
    }

    private void validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new IllegalArgumentException("Invalid email format");
        }
        if (email.length() > 255) {
            throw new IllegalArgumentException("Email cannot exceed 255 characters");
        }
    }

    private void validateFullName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new IllegalArgumentException("Full name cannot be null or empty");
        }
        if (fullName.length() < 2) {
            throw new IllegalArgumentException("Full name must be at least 2 characters long");
        }
        if (fullName.length() > 100) {
            throw new IllegalArgumentException("Full name cannot exceed 100 characters");
        }
    }

    // Getters for testing purposes
    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}