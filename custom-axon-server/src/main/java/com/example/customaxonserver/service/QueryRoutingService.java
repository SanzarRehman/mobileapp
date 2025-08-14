package com.example.customaxonserver.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Service responsible for routing queries to appropriate handlers
 * using Redis-based routing table and load balancing logic.
 */
@Service
public class QueryRoutingService {
    
    private static final Logger logger = LoggerFactory.getLogger(QueryRoutingService.class);
    
    private static final String QUERY_ROUTES_KEY = "query_routes";
    private static final String QUERY_INSTANCE_HEALTH_KEY = "query_instance_health";
    private static final String QUERY_HANDLER_REGISTRY_KEY = "query_handler_registry";
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public QueryRoutingService(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Routes a query to an appropriate handler instance using load balancing.
     * 
     * @param queryType The type of query to route
     * @return The instance ID that should handle the query
     * @throws QueryRoutingException if no healthy instances are available
     */
    public String routeQuery(String queryType) {
        logger.debug("Routing query {}", queryType);
        
        List<String> availableInstances = getHealthyInstances(queryType);
        
        if (availableInstances.isEmpty()) {
            logger.error("No healthy instances available for query type: {}", queryType);
            throw new QueryRoutingException("No healthy instances available for query type: " + queryType);
        }
        
        // Use round-robin load balancing for queries (unlike commands which use consistent hashing)
        String selectedInstance = selectInstanceForQuery(availableInstances, queryType);
        
        logger.debug("Selected instance {} for query {}", selectedInstance, queryType);
        
        return selectedInstance;
    }
    
    /**
     * Registers a query handler for a specific query type on an instance.
     * 
     * @param instanceId The instance ID
     * @param queryType The query type
     */
    public void registerQueryHandler(String instanceId, String queryType) {
        logger.info("Registering query handler for {} on instance {}", queryType, instanceId);
        
        try {
            // Add instance to the list of handlers for this query type
            String routesKey = QUERY_ROUTES_KEY + ":" + queryType;
            redisTemplate.opsForSet().add(routesKey, instanceId);
            
            // Update handler registry
            String registryKey = QUERY_HANDLER_REGISTRY_KEY + ":" + instanceId;
            redisTemplate.opsForSet().add(registryKey, queryType);
            
            // Mark instance as healthy
            updateInstanceHealth(instanceId, "healthy");
            
            logger.debug("Successfully registered query handler for {} on instance {}", queryType, instanceId);
            
        } catch (Exception e) {
            logger.error("Failed to register query handler for {} on instance {}: {}", 
                        queryType, instanceId, e.getMessage(), e);
            throw new QueryRoutingException("Failed to register query handler", e);
        }
    }
    
    /**
     * Unregisters a query handler for a specific query type on an instance.
     * 
     * @param instanceId The instance ID
     * @param queryType The query type
     */
    public void unregisterQueryHandler(String instanceId, String queryType) {
        logger.info("Unregistering query handler for {} on instance {}", queryType, instanceId);
        
        try {
            // Remove instance from the list of handlers for this query type
            String routesKey = QUERY_ROUTES_KEY + ":" + queryType;
            redisTemplate.opsForSet().remove(routesKey, instanceId);
            
            // Update handler registry
            String registryKey = QUERY_HANDLER_REGISTRY_KEY + ":" + instanceId;
            redisTemplate.opsForSet().remove(registryKey, queryType);
            
            logger.debug("Successfully unregistered query handler for {} on instance {}", queryType, instanceId);
            
        } catch (Exception e) {
            logger.error("Failed to unregister query handler for {} on instance {}: {}", 
                        queryType, instanceId, e.getMessage(), e);
            throw new QueryRoutingException("Failed to unregister query handler", e);
        }
    }
    
    /**
     * Updates the health status of an instance.
     * 
     * @param instanceId The instance ID
     * @param status The health status
     */
    public void updateInstanceHealth(String instanceId, String status) {
        try {
            Map<String, Object> healthInfo = new HashMap<>();
            healthInfo.put("status", status);
            healthInfo.put("last_heartbeat", Instant.now().toString());
            
            String healthKey = QUERY_INSTANCE_HEALTH_KEY + ":" + instanceId;
            redisTemplate.opsForHash().putAll(healthKey, healthInfo);
            
            // Set expiration for health info (instances must send heartbeats)
            redisTemplate.expire(healthKey, java.time.Duration.ofMinutes(2));
            
            logger.debug("Updated health status for query instance {} to {}", instanceId, status);
            
        } catch (Exception e) {
            logger.error("Failed to update health status for query instance {}: {}", instanceId, e.getMessage(), e);
        }
    }
    
    /**
     * Gets all registered query types for an instance.
     * 
     * @param instanceId The instance ID
     * @return Set of query types handled by the instance
     */
    public Set<String> getQueryTypesForInstance(String instanceId) {
        try {
            String registryKey = QUERY_HANDLER_REGISTRY_KEY + ":" + instanceId;
            Set<Object> queryTypes = redisTemplate.opsForSet().members(registryKey);
            
            Set<String> result = new HashSet<>();
            if (queryTypes != null) {
                for (Object queryType : queryTypes) {
                    result.add(queryType.toString());
                }
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("Failed to get query types for instance {}: {}", instanceId, e.getMessage(), e);
            return new HashSet<>();
        }
    }
    
    /**
     * Gets all instances that can handle a specific query type.
     * 
     * @param queryType The query type
     * @return List of instance IDs
     */
    public List<String> getInstancesForQueryType(String queryType) {
        try {
            String routesKey = QUERY_ROUTES_KEY + ":" + queryType;
            Set<Object> instances = redisTemplate.opsForSet().members(routesKey);
            
            List<String> result = new ArrayList<>();
            if (instances != null) {
                for (Object instance : instances) {
                    result.add(instance.toString());
                }
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("Failed to get instances for query type {}: {}", queryType, e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Gets health information for all query handler instances.
     * 
     * @return Map of instance ID to health status
     */
    public Map<String, String> getAllInstancesHealth() {
        Map<String, String> healthMap = new HashMap<>();
        
        try {
            Set<String> keys = redisTemplate.keys(QUERY_INSTANCE_HEALTH_KEY + ":*");
            if (keys != null) {
                for (String key : keys) {
                    String instanceId = key.substring((QUERY_INSTANCE_HEALTH_KEY + ":").length());
                    Map<Object, Object> healthInfo = redisTemplate.opsForHash().entries(key);
                    
                    if (!healthInfo.isEmpty()) {
                        String status = (String) healthInfo.get("status");
                        healthMap.put(instanceId, status != null ? status : "unknown");
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to get health information for all instances: {}", e.getMessage(), e);
        }
        
        return healthMap;
    }
    
    /**
     * Removes all routing information for an instance (cleanup when instance shuts down).
     * 
     * @param instanceId The instance ID
     */
    public void removeInstance(String instanceId) {
        logger.info("Removing all query routing information for instance {}", instanceId);
        
        try {
            // Get all query types for this instance
            Set<String> queryTypes = getQueryTypesForInstance(instanceId);
            
            // Remove instance from all query routes
            for (String queryType : queryTypes) {
                unregisterQueryHandler(instanceId, queryType);
            }
            
            // Remove instance health info
            String healthKey = QUERY_INSTANCE_HEALTH_KEY + ":" + instanceId;
            redisTemplate.delete(healthKey);
            
            // Remove handler registry for instance
            String registryKey = QUERY_HANDLER_REGISTRY_KEY + ":" + instanceId;
            redisTemplate.delete(registryKey);
            
            logger.info("Successfully removed all query routing information for instance {}", instanceId);
            
        } catch (Exception e) {
            logger.error("Failed to remove query routing information for instance {}: {}", instanceId, e.getMessage(), e);
            throw new QueryRoutingException("Failed to remove instance routing information", e);
        }
    }
    
    private List<String> getHealthyInstances(String queryType) {
        List<String> allInstances = getInstancesForQueryType(queryType);
        List<String> healthyInstances = new ArrayList<>();
        
        for (String instanceId : allInstances) {
            if (isInstanceHealthy(instanceId)) {
                healthyInstances.add(instanceId);
            }
        }
        
        return healthyInstances;
    }
    
    private boolean isInstanceHealthy(String instanceId) {
        try {
            String healthKey = QUERY_INSTANCE_HEALTH_KEY + ":" + instanceId;
            Map<Object, Object> healthInfo = redisTemplate.opsForHash().entries(healthKey);
            
            if (healthInfo.isEmpty()) {
                return false;
            }
            
            String status = (String) healthInfo.get("status");
            return "healthy".equals(status);
            
        } catch (Exception e) {
            logger.warn("Failed to check health for query instance {}: {}", instanceId, e.getMessage());
            return false;
        }
    }
    
    private String selectInstanceForQuery(List<String> instances, String queryType) {
        if (instances.size() == 1) {
            return instances.get(0);
        }
        
        // Use round-robin load balancing for queries
        // We use a simple random selection as a basic load balancing strategy
        // In a production system, you might want to implement proper round-robin
        // or weighted load balancing based on instance capacity
        int randomIndex = ThreadLocalRandom.current().nextInt(instances.size());
        return instances.get(randomIndex);
    }
    
    /**
     * Exception thrown when query routing fails.
     */
    public static class QueryRoutingException extends RuntimeException {
        public QueryRoutingException(String message) {
            super(message);
        }
        
        public QueryRoutingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}