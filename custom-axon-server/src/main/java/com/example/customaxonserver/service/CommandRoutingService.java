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
 * Service responsible for routing commands to appropriate handlers
 * using Redis-based routing table and load balancing logic.
 */
@Service
public class CommandRoutingService {
    
    private static final Logger logger = LoggerFactory.getLogger(CommandRoutingService.class);
    
    private static final String COMMAND_ROUTES_KEY = "command_routes";
    private static final String INSTANCE_HEALTH_KEY = "instance_health";
    private static final String HANDLER_REGISTRY_KEY = "handler_registry";
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public CommandRoutingService(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Routes a command to an appropriate handler instance.
     * 
     * @param commandType The type of command to route
     * @param aggregateId The aggregate ID for consistent routing
     * @return The instance ID that should handle the command
     * @throws CommandRoutingException if no healthy instances are available
     */
    public String routeCommand(String commandType, String aggregateId) {
        logger.debug("Routing command {} for aggregate {}", commandType, aggregateId);
        
        List<String> availableInstances = getHealthyInstances(commandType);
        
        if (availableInstances.isEmpty()) {
            logger.error("No healthy instances available for command type: {}", commandType);
            throw new CommandRoutingException("No healthy instances available for command type: " + commandType);
        }
        
        // Use consistent hashing based on aggregate ID to ensure same aggregate
        // always goes to the same instance (when available)
        String selectedInstance = selectInstanceForAggregate(availableInstances, aggregateId);
        
        logger.debug("Selected instance {} for command {} on aggregate {}", 
                    selectedInstance, commandType, aggregateId);
        
        return selectedInstance;
    }
    
    /**
     * Registers a command handler for a specific command type on an instance.
     * 
     * @param instanceId The instance ID
     * @param commandType The command type
     */
    public void registerCommandHandler(String instanceId, String commandType) {
        logger.info("Registering command handler for {} on instance {}", commandType, instanceId);
        
        try {
            // Add instance to the list of handlers for this command type
            String routesKey = COMMAND_ROUTES_KEY + ":" + commandType;
            redisTemplate.opsForSet().add(routesKey, instanceId);
            
            // Update handler registry
            String registryKey = HANDLER_REGISTRY_KEY + ":" + instanceId;
            redisTemplate.opsForSet().add(registryKey, commandType);
            
            // Mark instance as healthy
            updateInstanceHealth(instanceId, "healthy");
            
            logger.debug("Successfully registered command handler for {} on instance {}", commandType, instanceId);
            
        } catch (Exception e) {
            logger.error("Failed to register command handler for {} on instance {}: {}", 
                        commandType, instanceId, e.getMessage(), e);
            throw new CommandRoutingException("Failed to register command handler", e);
        }
    }
    
    /**
     * Unregisters a command handler for a specific command type on an instance.
     * 
     * @param instanceId The instance ID
     * @param commandType The command type
     */
    public void unregisterCommandHandler(String instanceId, String commandType) {
        logger.info("Unregistering command handler for {} on instance {}", commandType, instanceId);
        
        try {
            // Remove instance from the list of handlers for this command type
            String routesKey = COMMAND_ROUTES_KEY + ":" + commandType;
            redisTemplate.opsForSet().remove(routesKey, instanceId);
            
            // Update handler registry
            String registryKey = HANDLER_REGISTRY_KEY + ":" + instanceId;
            redisTemplate.opsForSet().remove(registryKey, commandType);
            
            logger.debug("Successfully unregistered command handler for {} on instance {}", commandType, instanceId);
            
        } catch (Exception e) {
            logger.error("Failed to unregister command handler for {} on instance {}: {}", 
                        commandType, instanceId, e.getMessage(), e);
            throw new CommandRoutingException("Failed to unregister command handler", e);
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
            
            String healthKey = INSTANCE_HEALTH_KEY + ":" + instanceId;
            redisTemplate.opsForHash().putAll(healthKey, healthInfo);
            
            // Set expiration for health info (instances must send heartbeats)
            redisTemplate.expire(healthKey, java.time.Duration.ofMinutes(2));
            
            logger.debug("Updated health status for instance {} to {}", instanceId, status);
            
        } catch (Exception e) {
            logger.error("Failed to update health status for instance {}: {}", instanceId, e.getMessage(), e);
        }
    }
    
    /**
     * Gets all registered command types for an instance.
     * 
     * @param instanceId The instance ID
     * @return Set of command types handled by the instance
     */
    public Set<String> getCommandTypesForInstance(String instanceId) {
        try {
            String registryKey = HANDLER_REGISTRY_KEY + ":" + instanceId;
            Set<Object> commandTypes = redisTemplate.opsForSet().members(registryKey);
            
            Set<String> result = new HashSet<>();
            if (commandTypes != null) {
                for (Object commandType : commandTypes) {
                    result.add(commandType.toString());
                }
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("Failed to get command types for instance {}: {}", instanceId, e.getMessage(), e);
            return new HashSet<>();
        }
    }
    
    /**
     * Gets all instances that can handle a specific command type.
     * 
     * @param commandType The command type
     * @return List of instance IDs
     */
    public List<String> getInstancesForCommandType(String commandType) {
        try {
            String routesKey = COMMAND_ROUTES_KEY + ":" + commandType;
            Set<Object> instances = redisTemplate.opsForSet().members(routesKey);
            
            List<String> result = new ArrayList<>();
            if (instances != null) {
                for (Object instance : instances) {
                    result.add(instance.toString());
                }
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("Failed to get instances for command type {}: {}", commandType, e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Removes all routing information for an instance (cleanup when instance shuts down).
     * 
     * @param instanceId The instance ID
     */
    public void removeInstance(String instanceId) {
        logger.info("Removing all routing information for instance {}", instanceId);
        
        try {
            // Get all command types for this instance
            Set<String> commandTypes = getCommandTypesForInstance(instanceId);
            
            // Remove instance from all command routes
            for (String commandType : commandTypes) {
                unregisterCommandHandler(instanceId, commandType);
            }
            
            // Remove instance health info
            String healthKey = INSTANCE_HEALTH_KEY + ":" + instanceId;
            redisTemplate.delete(healthKey);
            
            // Remove handler registry for instance
            String registryKey = HANDLER_REGISTRY_KEY + ":" + instanceId;
            redisTemplate.delete(registryKey);
            
            logger.info("Successfully removed all routing information for instance {}", instanceId);
            
        } catch (Exception e) {
            logger.error("Failed to remove routing information for instance {}: {}", instanceId, e.getMessage(), e);
            throw new CommandRoutingException("Failed to remove instance routing information", e);
        }
    }
    
    private List<String> getHealthyInstances(String commandType) {
        List<String> allInstances = getInstancesForCommandType(commandType);
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
            String healthKey = INSTANCE_HEALTH_KEY + ":" + instanceId;
            Map<Object, Object> healthInfo = redisTemplate.opsForHash().entries(healthKey);
            
            if (healthInfo.isEmpty()) {
                return false;
            }
            
            String status = (String) healthInfo.get("status");
            return "healthy".equals(status);
            
        } catch (Exception e) {
            logger.warn("Failed to check health for instance {}: {}", instanceId, e.getMessage());
            return false;
        }
    }
    
    private String selectInstanceForAggregate(List<String> instances, String aggregateId) {
        if (instances.size() == 1) {
            return instances.get(0);
        }
        
        // Use consistent hashing based on aggregate ID
        int hash = Math.abs(aggregateId.hashCode());
        int index = hash % instances.size();
        
        return instances.get(index);
    }
    
    /**
     * Exception thrown when command routing fails.
     */
    public static class CommandRoutingException extends RuntimeException {
        public CommandRoutingException(String message) {
            super(message);
        }
        
        public CommandRoutingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}