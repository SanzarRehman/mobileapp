package com.example.customaxonserver.controller;

import com.example.customaxonserver.model.QueryMessage;
import com.example.customaxonserver.model.QueryResponse;
import com.example.customaxonserver.service.QueryRoutingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST controller for query submission and routing management.
 */
@RestController
@RequestMapping("/api/queries")
@Validated
public class QueryController {
    
    private static final Logger logger = LoggerFactory.getLogger(QueryController.class);
    
    private final QueryRoutingService queryRoutingService;
    private final Counter queryProcessedCounter;
    private final Timer queryProcessingTimer;
    
    @Autowired
    public QueryController(QueryRoutingService queryRoutingService,
                          Counter queryProcessedCounter,
                          Timer queryProcessingTimer) {
        this.queryRoutingService = queryRoutingService;
        this.queryProcessedCounter = queryProcessedCounter;
        this.queryProcessingTimer = queryProcessingTimer;
    }
    
    /**
     * Submits a query for routing to appropriate handler.
     * 
     * @param queryMessage The query to route
     * @return Response indicating routing result
     */
    @PostMapping("/submit")
    public ResponseEntity<QueryResponse> submitQuery(@Valid @RequestBody QueryMessage queryMessage) {
        logger.info("Received query submission: {}", queryMessage);
        
        long startTime = System.nanoTime();
        try {
            String targetInstance = queryRoutingService.routeQuery(queryMessage.getQueryType());
            
            QueryResponse response = QueryResponse.routed(queryMessage.getQueryId(), targetInstance);
            
            logger.info("Successfully routed query {} to instance {}", 
                       queryMessage.getQueryId(), targetInstance);
            
            queryProcessedCounter.increment();
            return ResponseEntity.ok(response);
            
        } catch (QueryRoutingService.QueryRoutingException e) {
            logger.error("Failed to route query {}: {}", queryMessage.getQueryId(), e.getMessage());
            
            QueryResponse response = QueryResponse.error(queryMessage.getQueryId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            
        } catch (Exception e) {
            logger.error("Unexpected error routing query {}: {}", queryMessage.getQueryId(), e.getMessage(), e);
            
            QueryResponse response = QueryResponse.error(queryMessage.getQueryId(), 
                    "Internal server error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            queryProcessingTimer.record(System.nanoTime() - startTime, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }
    
    /**
     * Registers a query handler for a specific query type on an instance.
     * 
     * @param instanceId The instance ID
     * @param queryType The query type
     * @return Success response
     */
    @PostMapping("/handlers/{instanceId}/{queryType}")
    public ResponseEntity<String> registerHandler(
            @PathVariable String instanceId,
            @PathVariable String queryType) {
        
        logger.info("Registering query handler for {} on instance {}", queryType, instanceId);
        
        try {
            queryRoutingService.registerQueryHandler(instanceId, queryType);
            return ResponseEntity.ok("Query handler registered successfully");
            
        } catch (Exception e) {
            logger.error("Failed to register query handler for {} on instance {}: {}", 
                        queryType, instanceId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to register query handler: " + e.getMessage());
        }
    }
    
    /**
     * Unregisters a query handler for a specific query type on an instance.
     * 
     * @param instanceId The instance ID
     * @param queryType The query type
     * @return Success response
     */
    @DeleteMapping("/handlers/{instanceId}/{queryType}")
    public ResponseEntity<String> unregisterHandler(
            @PathVariable String instanceId,
            @PathVariable String queryType) {
        
        logger.info("Unregistering query handler for {} on instance {}", queryType, instanceId);
        
        try {
            queryRoutingService.unregisterQueryHandler(instanceId, queryType);
            return ResponseEntity.ok("Query handler unregistered successfully");
            
        } catch (Exception e) {
            logger.error("Failed to unregister query handler for {} on instance {}: {}", 
                        queryType, instanceId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to unregister query handler: " + e.getMessage());
        }
    }
    
    /**
     * Updates the health status of a query handler instance.
     * 
     * @param instanceId The instance ID
     * @param status The health status
     * @return Success response
     */
    @PostMapping("/instances/{instanceId}/health")
    public ResponseEntity<String> updateInstanceHealth(
            @PathVariable String instanceId,
            @RequestParam String status) {
        
        logger.debug("Updating health status for query instance {} to {}", instanceId, status);
        
        try {
            queryRoutingService.updateInstanceHealth(instanceId, status);
            return ResponseEntity.ok("Health status updated successfully");
            
        } catch (Exception e) {
            logger.error("Failed to update health status for query instance {}: {}", instanceId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to update health status: " + e.getMessage());
        }
    }
    
    /**
     * Gets all query types handled by a specific instance.
     * 
     * @param instanceId The instance ID
     * @return Set of query types
     */
    @GetMapping("/instances/{instanceId}/queries")
    public ResponseEntity<Set<String>> getQueryTypesForInstance(@PathVariable String instanceId) {
        logger.debug("Getting query types for instance {}", instanceId);
        
        try {
            Set<String> queryTypes = queryRoutingService.getQueryTypesForInstance(instanceId);
            return ResponseEntity.ok(queryTypes);
            
        } catch (Exception e) {
            logger.error("Failed to get query types for instance {}: {}", instanceId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
    
    /**
     * Gets all instances that can handle a specific query type.
     * 
     * @param queryType The query type
     * @return List of instance IDs
     */
    @GetMapping("/types/{queryType}/instances")
    public ResponseEntity<List<String>> getInstancesForQueryType(@PathVariable String queryType) {
        logger.debug("Getting instances for query type {}", queryType);
        
        try {
            List<String> instances = queryRoutingService.getInstancesForQueryType(queryType);
            return ResponseEntity.ok(instances);
            
        } catch (Exception e) {
            logger.error("Failed to get instances for query type {}: {}", queryType, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
    
    /**
     * Gets health information for all query handler instances.
     * 
     * @return Map of instance ID to health status
     */
    @GetMapping("/instances/health")
    public ResponseEntity<Map<String, String>> getAllInstancesHealth() {
        logger.debug("Getting health information for all query instances");
        
        try {
            Map<String, String> healthMap = queryRoutingService.getAllInstancesHealth();
            return ResponseEntity.ok(healthMap);
            
        } catch (Exception e) {
            logger.error("Failed to get health information for all query instances: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
    
    /**
     * Removes all routing information for an instance.
     * 
     * @param instanceId The instance ID
     * @return Success response
     */
    @DeleteMapping("/instances/{instanceId}")
    public ResponseEntity<String> removeInstance(@PathVariable String instanceId) {
        logger.info("Removing all query routing information for instance {}", instanceId);
        
        try {
            queryRoutingService.removeInstance(instanceId);
            return ResponseEntity.ok("Query instance removed successfully");
            
        } catch (Exception e) {
            logger.error("Failed to remove query instance {}: {}", instanceId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to remove query instance: " + e.getMessage());
        }
    }
}