package com.example.mainapplication.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;

/**
 * Event indicating that an existing user has been updated in the system.
 */
public class UserUpdatedEvent {

    private final String userId;
    private final String username;
    private final String email;
    private final String fullName;
    private final Instant updatedAt;

    @JsonCreator
    public UserUpdatedEvent(
        @JsonProperty("userId") String userId,
        @JsonProperty("username") String username,
        @JsonProperty("email") String email,
        @JsonProperty("fullName") String fullName,
        @JsonProperty("updatedAt") Instant updatedAt
    ) {
        this.userId = Objects.requireNonNull(userId, "User ID cannot be null");
        this.username = Objects.requireNonNull(username, "Username cannot be null");
        this.email = Objects.requireNonNull(email, "Email cannot be null");
        this.fullName = Objects.requireNonNull(fullName, "Full name cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Updated timestamp cannot be null");
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }



    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserUpdatedEvent that = (UserUpdatedEvent) o;
        return Objects.equals(userId, that.userId) &&
                Objects.equals(username, that.username) &&
                Objects.equals(email, that.email) &&
                Objects.equals(fullName, that.fullName) &&
                Objects.equals(updatedAt, that.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, username, email, fullName, updatedAt);
    }

    @Override
    public String toString() {
        return "UserUpdatedEvent{" +
                "userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", fullName='" + fullName + '\'' +
                ", updatedAt=" + updatedAt +
                '}';
    }
}