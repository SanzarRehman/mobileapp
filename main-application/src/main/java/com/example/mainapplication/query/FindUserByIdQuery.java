package com.example.mainapplication.query;

import java.util.Objects;

/**
 * Query to find a user by their unique identifier.
 */
public class FindUserByIdQuery {

    private final String userId;

    public FindUserByIdQuery(String userId) {
        this.userId = Objects.requireNonNull(userId, "User ID cannot be null");
    }

    public String getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FindUserByIdQuery that = (FindUserByIdQuery) o;
        return Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }

    @Override
    public String toString() {
        return "FindUserByIdQuery{" +
                "userId='" + userId + '\'' +
                '}';
    }
}