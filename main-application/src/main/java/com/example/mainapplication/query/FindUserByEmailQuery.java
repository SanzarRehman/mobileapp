package com.example.mainapplication.query;

import java.util.Objects;

/**
 * Query to find a user by their email address.
 */
public class FindUserByEmailQuery {

    private final String email;

    public FindUserByEmailQuery(String email) {
        this.email = Objects.requireNonNull(email, "Email cannot be null");
    }

    public String getEmail() {
        return email;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FindUserByEmailQuery that = (FindUserByEmailQuery) o;
        return Objects.equals(email, that.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email);
    }

    @Override
    public String toString() {
        return "FindUserByEmailQuery{" +
                "email='" + email + '\'' +
                '}';
    }
}