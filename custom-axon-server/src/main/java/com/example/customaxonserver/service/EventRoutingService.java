package com.example.customaxonserver.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Service responsible for routing events to appropriate handlers
 * using Redis-based routing table and load balancing logic.
 * 
 * Redis Key Architecture:
 * - event_routes:{eventType} -> Set of instanceIds that can handle this event type
 * - event_handler_registry:{instanceId} -> Set of event types this instance can handle
 * - instance_health:{instanceId} -> Hash of health information
 */
@Service
public class EventRoutingService {

    private static final Logger logger = LoggerFactory.getLogger(EventRoutingService.class);
    
    private static final String EVENT_ROUTES_KEY = "event_routes";
    private static final String EVENT_HANDLER_REGISTRY_KEY = "event_handler_registry";
    private static final String INSTANCE_HEALTH_KEY = "instance_health";
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final Random random = new Random();

    @Autowired
    public EventRoutingService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Get all instances that can handle a specific event type.
     * This is used for event broadcasting since events typically go to multiple handlers.
     * 
     * @param eventType The event type
     * @return List of healthy instance IDs that can handle this event
     */
    public List<String> getAllHandlersForEvent(String eventType) {
        logger.debug("Finding all handlers for event type: {}", eventType);
        
        try {
            String routesKey = EVENT_ROUTES_KEY + ":" + eventType;
            Set<Object> allInstances = redisTemplate.opsForSet().members(routesKey);
            
            if (allInstances == null || allInstances.isEmpty()) {
                logger.warn("No handlers found for event type: {}", eventType);
                return List.of();
            }

            // Filter only healthy instances
            List<String> healthyInstances = allInstances.stream()
                    .map(Object::toString)
                    .filter(this::isInstanceHealthy)
                    .toList();

            logger.debug("Found {} healthy handlers for event type {}", healthyInstances.size(), eventType);
            return healthyInstances;
            
        } catch (Exception e) {
            logger.error("Failed to get handlers for event type {}: {}", eventType, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Registers an event handler for a specific event type on an instance.
     * 
     * @param instanceId The instance ID
     * @param eventType The event type
     */
    public void registerEventHandler(String instanceId, String eventType) {
        logger.info("Registering event handler for {} on instance {}", eventType, instanceId);
        
        try {
            // Add instance to the list of handlers for this event type
            String routesKey = EVENT_ROUTES_KEY + ":" + eventType;
            redisTemplate.opsForSet().add(routesKey, instanceId);
            
            // Update handler registry
            String registryKey = EVENT_HANDLER_REGISTRY_KEY + ":" + instanceId;
            redisTemplate.opsForSet().add(registryKey, eventType);
            
            // Mark instance as healthy
            updateInstanceHealth(instanceId, "healthy");
            
            logger.debug("Successfully registered event handler for {} on instance {}", eventType, instanceId);
            
        } catch (Exception e) {
            logger.error("Failed to register event handler for {} on instance {}: {}", 
                        eventType, instanceId, e.getMessage(), e);
            throw new EventRoutingException("Failed to register event handler", e);
        }
    }
    
    /**
     * Unregisters an event handler for a specific event type on an instance.
     * 
     * @param instanceId The instance ID
     * @param eventType The event type
     */
    public void unregisterEventHandler(String instanceId, String eventType) {
        logger.info("Unregistering event handler for {} on instance {}", eventType, instanceId);
        
        try {
            // Remove instance from the list of handlers for this event type
            String routesKey = EVENT_ROUTES_KEY + ":" + eventType;
            redisTemplate.opsForSet().remove(routesKey, instanceId);
            
            // Update handler registry
            String registryKey = EVENT_HANDLER_REGISTRY_KEY + ":" + instanceId;
            redisTemplate.opsForSet().remove(registryKey, eventType);
            
            logger.debug("Successfully unregistered event handler for {} on instance {}", eventType, instanceId);
            
        } catch (Exception e) {
            logger.error("Failed to unregister event handler for {} on instance {}: {}", 
                        eventType, instanceId, e.getMessage(), e);
            throw new EventRoutingException("Failed to unregister event handler", e);
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
            String healthKey = INSTANCE_HEALTH_KEY + ":" + instanceId;
            redisTemplate.opsForHash().put(healthKey, "status", status);
            redisTemplate.opsForHash().put(healthKey, "last_update", System.currentTimeMillis());
            redisTemplate.expire(healthKey, Duration.ofMinutes(2));
            
            logger.trace("Updated health status for instance {} to {}", instanceId, status);
            
        } catch (Exception e) {
            logger.error("Failed to update health status for instance {}: {}", instanceId, e.getMessage(), e);
        }
    }
    
    /**
     * Checks if an instance is healthy.
     * 
     * @param instanceId The instance ID
     * @return true if the instance is healthy, false otherwise
     */
    private boolean isInstanceHealthy(String instanceId) {
        try {
            String healthKey = INSTANCE_HEALTH_KEY + ":" + instanceId;
            Object status = redisTemplate.opsForHash().get(healthKey, "status");
            return "healthy".equals(status);
        } catch (Exception e) {
            logger.debug("Failed to check health for instance {}: {}", instanceId, e.getMessage());
            return false;
        }
    }

    /**
     * Get all event types handled by a specific instance.
     * 
     * @param instanceId The instance ID
     * @return Set of event types handled by this instance
     */
    public Set<Object> getEventTypesForInstance(String instanceId) {
        try {
            String registryKey = EVENT_HANDLER_REGISTRY_KEY + ":" + instanceId;
            return redisTemplate.opsForSet().members(registryKey);
        } catch (Exception e) {
            logger.error("Failed to get event types for instance {}: {}", instanceId, e.getMessage());
            return Set.of();
        }
    }

    /**
     * Get count of handlers for a specific event type.
     * 
     * @param eventType The event type
     * @return Number of instances that can handle this event type
     */
    public long getHandlerCount(String eventType) {
        try {
            String routesKey = EVENT_ROUTES_KEY + ":" + eventType;
            return redisTemplate.opsForSet().size(routesKey);
        } catch (Exception e) {
            logger.error("Failed to get handler count for event type {}: {}", eventType, e.getMessage());
            return 0;
        }
    }

    /**
     * Cleanup stale event handler registrations for an instance.
     * 
     * @param instanceId The instance ID to cleanup
     */
    public void cleanupInstance(String instanceId) {
        logger.info("Cleaning up event handler registrations for instance: {}", instanceId);
        
        try {
            // Get all event types registered for this instance
            Set<Object> eventTypes = getEventTypesForInstance(instanceId);
            
            // Remove instance from all event routes
            for (Object eventType : eventTypes) {
                String routesKey = EVENT_ROUTES_KEY + ":" + eventType;
                redisTemplate.opsForSet().remove(routesKey, instanceId);
                logger.debug("Removed instance {} from event routes for {}", instanceId, eventType);
            }
            
            // Remove the registry entry for this instance
            String registryKey = EVENT_HANDLER_REGISTRY_KEY + ":" + instanceId;
            redisTemplate.delete(registryKey);
            
            // Remove health entry
            String healthKey = INSTANCE_HEALTH_KEY + ":" + instanceId;
            redisTemplate.delete(healthKey);
            
            logger.info("Successfully cleaned up event handler registrations for instance: {}", instanceId);
            
        } catch (Exception e) {
            logger.error("Failed to cleanup instance {}: {}", instanceId, e.getMessage(), e);
        }
    }

    /**
     * Get statistics about event routing.
     * 
     * @return EventRoutingStats object with current statistics
     */
    public EventRoutingStats getStats() {
        try {
            Set<String> allEventRouteKeys = redisTemplate.keys(EVENT_ROUTES_KEY + ":*");
            Set<String> allRegistryKeys = redisTemplate.keys(EVENT_HANDLER_REGISTRY_KEY + ":*");
            
            int totalEventTypes = allEventRouteKeys != null ? allEventRouteKeys.size() : 0;
            int totalInstances = allRegistryKeys != null ? allRegistryKeys.size() : 0;
            
            return new EventRoutingStats(totalEventTypes, totalInstances);
            
        } catch (Exception e) {
            logger.error("Failed to get event routing stats: {}", e.getMessage(), e);
            return new EventRoutingStats(0, 0);
        }
    }

    /**
     * Statistics class for event routing.
     */
    public static class EventRoutingStats {
        private final int totalEventTypes;
        private final int totalInstances;

        public EventRoutingStats(int totalEventTypes, int totalInstances) {
            this.totalEventTypes = totalEventTypes;
            this.totalInstances = totalInstances;
        }

        public int getTotalEventTypes() { return totalEventTypes; }
        public int getTotalInstances() { return totalInstances; }

        @Override
        public String toString() {
            return String.format("EventRoutingStats{eventTypes=%d, instances=%d}", 
                                totalEventTypes, totalInstances);
        }
    }

    /**
     * Custom exception for event routing operations.
     */
    public static class EventRoutingException extends RuntimeException {
        public EventRoutingException(String message) {
            super(message);
        }

        public EventRoutingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
