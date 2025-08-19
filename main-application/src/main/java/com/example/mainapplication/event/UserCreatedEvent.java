package com.example.mainapplication.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;

/**
 * Event indicating that a new user has been created in the system.
 */
public class UserCreatedEvent {

    private final String userId;
    private final String username;
    private final String email;
    private final String fullName;
    private final Instant createdAt;



    @JsonCreator
    public UserCreatedEvent(
        @JsonProperty("userId") String userId,
        @JsonProperty("username") String username,
        @JsonProperty("email") String email,
        @JsonProperty("fullName") String fullName,
        @JsonProperty("createdAt") Instant createdAt
    ) {
        this.userId = Objects.requireNonNull(userId, "User ID cannot be null");
        this.username = Objects.requireNonNull(username, "Username cannot be null");
        this.email = Objects.requireNonNull(email, "Email cannot be null");
        this.fullName = Objects.requireNonNull(fullName, "Full name cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "Updated timestamp cannot be null");
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserCreatedEvent that = (UserCreatedEvent) o;
        return Objects.equals(userId, that.userId) &&
                Objects.equals(username, that.username) &&
                Objects.equals(email, that.email) &&
                Objects.equals(fullName, that.fullName) &&
                Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, username, email, fullName, createdAt);
    }

    @Override
    public String toString() {
        return "UserCreatedEvent{" +
                "userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", fullName='" + fullName + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}