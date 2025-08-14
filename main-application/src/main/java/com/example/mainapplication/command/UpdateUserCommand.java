package com.example.mainapplication.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.util.Objects;

/**
 * Command to update an existing user in the system.
 */
public class UpdateUserCommand {

    @TargetAggregateIdentifier
    private final String userId;
    private final String username;
    private final String email;
    private final String fullName;

    public UpdateUserCommand(String userId, String username, String email, String fullName) {
        this.userId = Objects.requireNonNull(userId, "User ID cannot be null");
        this.username = Objects.requireNonNull(username, "Username cannot be null");
        this.email = Objects.requireNonNull(email, "Email cannot be null");
        this.fullName = Objects.requireNonNull(fullName, "Full name cannot be null");
    }

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UpdateUserCommand that = (UpdateUserCommand) o;
        return Objects.equals(userId, that.userId) &&
                Objects.equals(username, that.username) &&
                Objects.equals(email, that.email) &&
                Objects.equals(fullName, that.fullName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, username, email, fullName);
    }

    @Override
    public String toString() {
        return "UpdateUserCommand{" +
                "userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", fullName='" + fullName + '\'' +
                '}';
    }
}