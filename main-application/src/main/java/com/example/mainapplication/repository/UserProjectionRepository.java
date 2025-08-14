package com.example.mainapplication.repository;

import com.example.mainapplication.projection.UserProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for UserProjection operations.
 * Provides methods for querying user projection data.
 */
@Repository
public interface UserProjectionRepository extends JpaRepository<UserProjection, String> {

    /**
     * Find user by email address.
     */
    Optional<UserProjection> findByEmail(String email);

    /**
     * Find users by status.
     */
    List<UserProjection> findByStatus(String status);

    /**
     * Find users by name containing (case-insensitive search).
     */
    List<UserProjection> findByNameContainingIgnoreCase(String name);

    /**
     * Find users created after a specific date.
     */
    List<UserProjection> findByCreatedAtAfter(OffsetDateTime createdAt);

    /**
     * Find users updated after a specific date.
     */
    List<UserProjection> findByUpdatedAtAfter(OffsetDateTime updatedAt);

    /**
     * Check if a user exists with the given email.
     */
    boolean existsByEmail(String email);

    /**
     * Count users by status.
     */
    long countByStatus(String status);

    /**
     * Find users with pagination support.
     */
    @Query("SELECT u FROM UserProjection u ORDER BY u.createdAt DESC")
    List<UserProjection> findAllOrderByCreatedAtDesc();

    /**
     * Search users by name or email.
     */
    @Query("SELECT u FROM UserProjection u WHERE " +
           "LOWER(u.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<UserProjection> searchByNameOrEmail(@Param("searchTerm") String searchTerm);
}