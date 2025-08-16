package com.example.mainapplication.handler;

import com.example.mainapplication.projection.UserProjection;
import com.example.mainapplication.query.FindAllUsersQuery;
import com.example.mainapplication.query.FindUserByEmailQuery;
import com.example.mainapplication.query.FindUserByIdQuery;
import com.example.mainapplication.repository.UserProjectionRepository;
import org.axonframework.queryhandling.QueryHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Query handler for processing user-related queries against projections.
 * This handler provides read-side query capabilities for user data.
 */
@Component
public class UserQueryHandler {

    private static final Logger logger = LoggerFactory.getLogger(UserQueryHandler.class);

    private final UserProjectionRepository userProjectionRepository;

    @Autowired
    public UserQueryHandler(UserProjectionRepository userProjectionRepository) {
        this.userProjectionRepository = userProjectionRepository;
    }

    /**
     * Handles FindUserByIdQuery to retrieve a user by their ID.
     * 
     * @param query The FindUserByIdQuery to process
     * @return Optional containing the user projection if found
     */
    @QueryHandler
    public Optional<UserProjection> handle(FindUserByIdQuery query) {
        logger.debug("Processing FindUserByIdQuery for user: {}", query.getUserId());
        
        try {
            Optional<UserProjection> result = userProjectionRepository.findById(query.getUserId());
            
            if (result.isPresent()) {
                logger.debug("Found user projection for user: {}", query.getUserId());
            } else {
                logger.debug("No user projection found for user: {}", query.getUserId());
            }
            
            return result;
        } catch (Exception e) {
            logger.error("Error processing FindUserByIdQuery for user: {}", query.getUserId(), e);
            throw e;
        }
    }

    /**
     * Handles FindUserByEmailQuery to retrieve a user by their email.
     * 
     * @param query The FindUserByEmailQuery to process
     * @return Optional containing the user projection if found
     */
    @QueryHandler
    public Optional<UserProjection> handle(FindUserByEmailQuery query) {
        logger.debug("Processing FindUserByEmailQuery for email: {}", query.getEmail());
        
        try {
            Optional<UserProjection> result = userProjectionRepository.findByEmail(query.getEmail());
            
            if (result.isPresent()) {
                logger.debug("Found user projection for email: {}", query.getEmail());
            } else {
                logger.debug("No user projection found for email: {}", query.getEmail());
            }
            
            return result;
        } catch (Exception e) {
            logger.error("Error processing FindUserByEmailQuery for email: {}", query.getEmail(), e);
            throw e;
        }
    }

    /**
     * Handles FindAllUsersQuery to retrieve users with optional filtering.
     * 
     * @param query The FindAllUsersQuery to process
     * @return List of user projections matching the criteria
     */
    @QueryHandler
    public List<UserProjection> handle(FindAllUsersQuery query) {
        logger.debug("Processing FindAllUsersQuery with status: {}, searchTerm: {}, page: {}, size: {}", 
                    query.getStatus(), query.getSearchTerm(), query.getPage(), query.getSize());
        
        try {
            List<UserProjection> result;
            
            // Apply filtering based on query parameters
            if (query.getSearchTerm() != null && !query.getSearchTerm().trim().isEmpty()) {
                // Search by name or email
                result = userProjectionRepository.searchByNameOrEmail(query.getSearchTerm().trim());
                logger.debug("Found {} users matching search term: {}", result.size(), query.getSearchTerm());
            } else if (query.getStatus() != null && !query.getStatus().trim().isEmpty()) {
                // Filter by status
                result = userProjectionRepository.findByStatus(query.getStatus().trim());
                logger.debug("Found {} users with status: {}", result.size(), query.getStatus());
            } else {
                // Get all users ordered by creation date
                result = userProjectionRepository.findAllByOrderByCreatedAtDesc();
                logger.debug("Found {} total users", result.size());
            }
            
            // Apply pagination manually since we're using custom queries
            int fromIndex = query.getPage() * query.getSize();
            int toIndex = Math.min(fromIndex + query.getSize(), result.size());
            
            if (fromIndex >= result.size()) {
                return List.of(); // Empty list if page is beyond available data
            }
            
            List<UserProjection> paginatedResult = result.subList(fromIndex, toIndex);
            logger.debug("Returning {} users for page {} with size {}", paginatedResult.size(), query.getPage(), query.getSize());
            
            return paginatedResult;
        } catch (Exception e) {
            logger.error("Error processing FindAllUsersQuery", e);
            throw e;
        }
    }
}