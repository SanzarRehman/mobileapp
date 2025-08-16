package com.example.mainapplication.repository;

import com.example.mainapplication.projection.UserProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserProjectionRepository extends JpaRepository<UserProjection, String> {
    
    Optional<UserProjection> findByEmail(String email);
    
    @Query("SELECT u FROM UserProjection u WHERE u.username LIKE %:searchTerm% OR u.email LIKE %:searchTerm%")
    List<UserProjection> searchByNameOrEmail(@Param("searchTerm") String searchTerm);
    
    List<UserProjection> findByStatus(String status);
    
    List<UserProjection> findAllByOrderByCreatedAtDesc();
}