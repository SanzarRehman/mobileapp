package com.example.mainapplication.query;

import java.util.Objects;

/**
 * Query to find all users with optional filtering.
 */
public class FindAllUsersQuery {

    private final String status;
    private final String searchTerm;
    private final int page;
    private final int size;

    public FindAllUsersQuery() {
        this(null, null, 0, 20);
    }

    public FindAllUsersQuery(String status, String searchTerm, int page, int size) {
        this.status = status;
        this.searchTerm = searchTerm;
        this.page = Math.max(0, page);
        this.size = Math.max(1, size);
    }

    public String getStatus() {
        return status;
    }

    public String getSearchTerm() {
        return searchTerm;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FindAllUsersQuery that = (FindAllUsersQuery) o;
        return page == that.page &&
                size == that.size &&
                Objects.equals(status, that.status) &&
                Objects.equals(searchTerm, that.searchTerm);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, searchTerm, page, size);
    }

    @Override
    public String toString() {
        return "FindAllUsersQuery{" +
                "status='" + status + '\'' +
                ", searchTerm='" + searchTerm + '\'' +
                ", page=" + page +
                ", size=" + size +
                '}';
    }
}