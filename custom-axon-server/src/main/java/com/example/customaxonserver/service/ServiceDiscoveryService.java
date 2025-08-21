package com.example.customaxonserver.service;

import com.example.grpc.common.HealthStatus;
import com.example.grpc.common.ServiceInstance;
import com.example.customaxonserver.dto.ServiceInstanceDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Service Discovery implementation using Redis as the registry.
 * Manages service registration, health monitoring, and auto-discovery.
 */
@Service
public class ServiceDiscoveryService {

    private static final Logger logger = LoggerFactory.getLogger(ServiceDiscoveryService.class);
    
    private static final String SERVICE_REGISTRY_KEY = "service_registry";
    private static final String SERVICE_HEALTH_KEY = "service_health";
    private static final String SERVICE_INSTANCES_KEY = "service_instances";
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    // In-memory cache for faster lookups
    private final ConcurrentMap<String, ServiceInstance> serviceCache = new ConcurrentHashMap<>();

    @Autowired
    public ServiceDiscoveryService(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Register a service instance in the registry.
     */
    public void registerService(ServiceInstance serviceInstance) {
        logger.info("Registering service instance: {} for service: {}", 
                   serviceInstance.getInstanceId(), serviceInstance.getServiceName());
        
        try {
            // Convert to DTO for JSON serialization
            ServiceInstanceDto dto = new ServiceInstanceDto(serviceInstance);
            String serviceJson = objectMapper.writeValueAsString(dto);
            
            // Store in Redis with multiple indexes
            String instanceKey = SERVICE_INSTANCES_KEY + ":" + serviceInstance.getInstanceId();
            String serviceKey = SERVICE_REGISTRY_KEY + ":" + serviceInstance.getServiceName();
            String healthKey = SERVICE_HEALTH_KEY + ":" + serviceInstance.getInstanceId();
            
            // Store service instance data
            redisTemplate.opsForValue().set(instanceKey, serviceJson);
            redisTemplate.expire(instanceKey, Duration.ofMinutes(5)); // TTL for cleanup
            
            // Add to service type index
            redisTemplate.opsForSet().add(serviceKey, serviceInstance.getInstanceId());
            
            // Store health information
            Map<String, Object> healthInfo = new HashMap<>();
            healthInfo.put("status", serviceInstance.getStatus().name());
            healthInfo.put("last_heartbeat", serviceInstance.getLastHeartbeat());
            healthInfo.put("host", serviceInstance.getHost());
            healthInfo.put("port", serviceInstance.getPort());
            
            redisTemplate.opsForHash().putAll(healthKey, healthInfo);
            redisTemplate.expire(healthKey, Duration.ofMinutes(2)); // Health TTL
            
            // Update local cache
            serviceCache.put(serviceInstance.getInstanceId(), serviceInstance);
            
            logger.debug("Successfully registered service instance: {}", serviceInstance.getInstanceId());
            
        } catch (Exception e) {
            logger.error("Failed to register service instance: {}", serviceInstance.getInstanceId(), e);
            throw new ServiceDiscoveryException("Failed to register service instance", e);
        }
    }

    /**
     * Unregister a service instance from the registry.
     */
    public void unregisterService(String instanceId) {
        logger.info("Unregistering service instance: {}", instanceId);
        
        try {
            ServiceInstance serviceInstance = getServiceInstance(instanceId);
            
            if (serviceInstance != null) {
                // Remove from service type index
                String serviceKey = SERVICE_REGISTRY_KEY + ":" + serviceInstance.getServiceName();
                redisTemplate.opsForSet().remove(serviceKey, instanceId);
            }
            
            // Remove instance data
            String instanceKey = SERVICE_INSTANCES_KEY + ":" + instanceId;
            String healthKey = SERVICE_HEALTH_KEY + ":" + instanceId;
            
            redisTemplate.delete(instanceKey);
            redisTemplate.delete(healthKey);
            
            // Remove from local cache
            serviceCache.remove(instanceId);
            
            logger.debug("Successfully unregistered service instance: {}", instanceId);
            
        } catch (Exception e) {
            logger.error("Failed to unregister service instance: {}", instanceId, e);
            throw new ServiceDiscoveryException("Failed to unregister service instance", e);
        }
    }

    /**
     * Get a specific service instance by ID.
     */
    public ServiceInstance getServiceInstance(String instanceId) {
        try {
            // Check cache first
            ServiceInstance cached = serviceCache.get(instanceId);
            if (cached != null) {
                return cached;
            }
            
            // Load from Redis
            String instanceKey = SERVICE_INSTANCES_KEY + ":" + instanceId;
            String serviceJson = (String) redisTemplate.opsForValue().get(instanceKey);
            
            if (serviceJson != null) {
                // Deserialize DTO and convert to ServiceInstance
                ServiceInstanceDto dto = objectMapper.readValue(serviceJson, ServiceInstanceDto.class);
                ServiceInstance serviceInstance = dto.toServiceInstance();
                
                // Update cache
                serviceCache.put(instanceId, serviceInstance);
                
                return serviceInstance;
            }
            
            return null;
            
        } catch (Exception e) {
            logger.error("Failed to get service instance: {}", instanceId, e);
            return null;
        }
    }

    /**
     * Get all healthy instances for a specific service.
     */
    public List<ServiceInstance> getHealthyServices(String serviceName) {
        return getHealthyServices(serviceName, Collections.emptyList());
    }

    /**
     * Get all healthy instances for a specific service with optional tags.
     */
    public List<ServiceInstance> getHealthyServices(String serviceName, List<String> tags) {
        logger.debug("Getting healthy services for: {} with tags: {}", serviceName, tags);
        
        try {
            List<ServiceInstance> healthyServices = new ArrayList<>();
            String serviceKey = SERVICE_REGISTRY_KEY + ":" + serviceName;
            
            Set<Object> instanceIds = redisTemplate.opsForSet().members(serviceKey);
            if (instanceIds != null) {
                for (Object instanceIdObj : instanceIds) {
                    String instanceId = instanceIdObj.toString();
                    ServiceInstance serviceInstance = getServiceInstance(instanceId);
                    
                    if (serviceInstance != null && 
                        serviceInstance.getStatus() == HealthStatus.HEALTHY &&
                        (tags.isEmpty() || serviceInstance.getTagsList().containsAll(tags))) {
                        healthyServices.add(serviceInstance);
                    }
                }
            }
            
            logger.debug("Found {} healthy services for: {}", healthyServices.size(), serviceName);
            return healthyServices;
            
        } catch (Exception e) {
            logger.error("Failed to get healthy services for: {}", serviceName, e);
            return Collections.emptyList();
        }
    }

    /**
     * Update the health status of a service instance.
     */
    public void updateServiceHealth(String instanceId, HealthStatus status) {
        logger.trace("Updating health status for instance: {} to: {}", instanceId, status);
        
        try {
            String healthKey = SERVICE_HEALTH_KEY + ":" + instanceId;
            
            Map<String, Object> healthInfo = new HashMap<>();
            healthInfo.put("status", status.name());
            healthInfo.put("last_heartbeat", Instant.now().toEpochMilli());
            
            redisTemplate.opsForHash().putAll(healthKey, healthInfo);
            redisTemplate.expire(healthKey, Duration.ofMinutes(2));
            
            // Update cached instance if exists
            ServiceInstance cached = serviceCache.get(instanceId);
            if (cached != null) {
                ServiceInstance updated = cached.toBuilder()
                        .setStatus(status)
                        .setLastHeartbeat(Instant.now().toEpochMilli())
                        .build();
                serviceCache.put(instanceId, updated);
            }
            
        } catch (Exception e) {
            logger.error("Failed to update health status for instance: {}", instanceId, e);
        }
    }

    /**
     * Get all services of a specific type (healthy and unhealthy).
     */
    public List<ServiceInstance> getAllServices(String serviceName) {
        logger.debug("Getting all services for: {}", serviceName);
        
        try {
            List<ServiceInstance> allServices = new ArrayList<>();
            String serviceKey = SERVICE_REGISTRY_KEY + ":" + serviceName;
            
            Set<Object> instanceIds = redisTemplate.opsForSet().members(serviceKey);
            if (instanceIds != null) {
                for (Object instanceIdObj : instanceIds) {
                    String instanceId = instanceIdObj.toString();
                    ServiceInstance serviceInstance = getServiceInstance(instanceId);
                    
                    if (serviceInstance != null) {
                        allServices.add(serviceInstance);
                    }
                }
            }
            
            return allServices;
            
        } catch (Exception e) {
            logger.error("Failed to get all services for: {}", serviceName, e);
            return Collections.emptyList();
        }
    }

    /**
     * Auto-discover services based on command types.
     */
    public List<ServiceInstance> discoverServicesByCommandType(String commandType) {
        logger.debug("Discovering services that handle command type: {}", commandType);
        
        try {
            List<ServiceInstance> matchingServices = new ArrayList<>();
            
            // Get all service instances from cache first
            for (ServiceInstance serviceInstance : serviceCache.values()) {
                if (serviceInstance.getCommandTypesList().contains(commandType)) {
                    matchingServices.add(serviceInstance);
                }
            }
            
            // If cache is empty or incomplete, scan Redis
            if (matchingServices.isEmpty()) {
                Set<String> keys = redisTemplate.keys(SERVICE_INSTANCES_KEY + ":*");
                if (keys != null) {
                    for (String key : keys) {
                        String serviceJson = (String) redisTemplate.opsForValue().get(key);
                        if (serviceJson != null) {
                            try {
                                // Deserialize DTO and convert to ServiceInstance
                                ServiceInstanceDto dto = objectMapper.readValue(serviceJson, ServiceInstanceDto.class);
                                ServiceInstance serviceInstance = dto.toServiceInstance();
                                if (serviceInstance.getCommandTypesList().contains(commandType)) {
                                    matchingServices.add(serviceInstance);
                                    // Update cache
                                    serviceCache.put(serviceInstance.getInstanceId(), serviceInstance);
                                }
                            } catch (JsonProcessingException e) {
                                logger.warn("Failed to parse service instance JSON from key: {}", key, e);
                            }
                        }
                    }
                }
            }
            
            logger.debug("Found {} services for command type: {}", matchingServices.size(), commandType);
            return matchingServices;
            
        } catch (Exception e) {
            logger.error("Failed to discover services for command type: {}", commandType, e);
            return Collections.emptyList();
        }
    }

    /**
     * Get health status of all registered services.
     */
    public Map<String, HealthStatus> getAllServiceHealth() {
        logger.debug("Getting health status for all services");
        
        try {
            Map<String, HealthStatus> healthMap = new HashMap<>();
            Set<String> healthKeys = redisTemplate.keys(SERVICE_HEALTH_KEY + ":*");
            
            if (healthKeys != null) {
                for (String key : healthKeys) {
                    String instanceId = key.substring((SERVICE_HEALTH_KEY + ":").length());
                    Map<Object, Object> healthInfo = redisTemplate.opsForHash().entries(key);
                    
                    if (!healthInfo.isEmpty()) {
                        String status = (String) healthInfo.get("status");
                        try {
                            HealthStatus healthStatus = HealthStatus.valueOf(status);
                            healthMap.put(instanceId, healthStatus);
                        } catch (IllegalArgumentException e) {
                            logger.warn("Invalid health status for instance {}: {}", instanceId, status);
                            healthMap.put(instanceId, HealthStatus.UNKNOWN);
                        }
                    }
                }
            }
            
            return healthMap;
            
        } catch (Exception e) {
            logger.error("Failed to get health status for all services", e);
            return Collections.emptyMap();
        }
    }

    /**
     * Clean up expired service instances.
     */
    public void cleanupExpiredServices() {
        logger.debug("Cleaning up expired service instances");
        
        try {
            long currentTime = Instant.now().toEpochMilli();
            List<String> expiredInstances = new ArrayList<>();
            
            for (Map.Entry<String, ServiceInstance> entry : serviceCache.entrySet()) {
                ServiceInstance instance = entry.getValue();
                // Consider instance expired if no heartbeat for more than 2 minutes
                if (currentTime - instance.getLastHeartbeat() > 120000) {
                    expiredInstances.add(entry.getKey());
                }
            }
            
            for (String instanceId : expiredInstances) {
                logger.info("Cleaning up expired service instance: {}", instanceId);
                unregisterService(instanceId);
            }
            
            if (!expiredInstances.isEmpty()) {
                logger.info("Cleaned up {} expired service instances", expiredInstances.size());
            }
            
        } catch (Exception e) {
            logger.error("Failed to cleanup expired services", e);
        }
    }

    /**
     * Exception thrown when service discovery operations fail.
     */
    public static class ServiceDiscoveryException extends RuntimeException {
        public ServiceDiscoveryException(String message) {
            super(message);
        }
        
        public ServiceDiscoveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}