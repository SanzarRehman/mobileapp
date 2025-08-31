package com.example.customaxonserver.service;

import com.example.customaxonserver.dto.ServiceInstanceDto;
import com.example.grpc.common.*;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service for handling streaming heartbeats from command handler instances.
 * This implements server streaming where clients request health updates
 * and the server streams health status changes back to them.
 */
@Service
public class StreamingHeartbeatService {
    
    private static final Logger logger = LoggerFactory.getLogger(StreamingHeartbeatService.class);
    private static final String INSTANCE_HEALTH_KEY = "instance_health";
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ScheduledExecutorService cleanupExecutor;
    private final ScheduledExecutorService healthBroadcastExecutor;
    
    // Track active streaming connections - clients listening for health updates
    private final Map<String, StreamObserver<HealthStreamResponse>> healthStreamClients = new ConcurrentHashMap<>();
    private final Map<String, Long> lastHeartbeatTimes = new ConcurrentHashMap<>();
    
    @Autowired
    public StreamingHeartbeatService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.cleanupExecutor = Executors.newScheduledThreadPool(2);
        this.healthBroadcastExecutor = Executors.newSingleThreadScheduledExecutor();
        
        // Start cleanup task to remove stale instances
        startCleanupTask();
        // Start periodic health broadcast
        startHealthBroadcast();
    }
    
    /**
     * Register a client for health stream updates.
     * This is called when a client requests a health stream.
     */
    public void registerHealthStreamClient(String instanceId, StreamObserver<HealthStreamResponse> responseObserver) {
        logger.info("Registering health stream client for instance: {}", instanceId);
        
        healthStreamClients.put(instanceId, responseObserver);
        
        // Send initial health status if available
        try {
            String healthKey = INSTANCE_HEALTH_KEY + ":" + instanceId;
            Map<Object, Object> healthInfo = redisTemplate.opsForHash().entries(healthKey);
            
            if (!healthInfo.isEmpty()) {
                String status = (String) healthInfo.get("status");
                Long lastHeartbeat = (Long) healthInfo.get("last_heartbeat");
                
                HealthStreamResponse currentHealth = HealthStreamResponse.newBuilder()
                        .setInstanceId(instanceId)
                        .setStatus(status != null ? HealthStatus.valueOf(status.toUpperCase()) : HealthStatus.UNKNOWN)
                        .setTimestamp(lastHeartbeat != null ? lastHeartbeat : System.currentTimeMillis())
                        .putMetadata("source", "existing_health_data")
                        .build();
                
                responseObserver.onNext(currentHealth);
            }
        } catch (Exception e) {
            logger.warn("Failed to send initial health status to client {}: {}", instanceId, e.getMessage());
        }
    }
    
    /**
     * Update instance health information and broadcast to listening clients.
     * This is called when an instance sends a unary heartbeat.
     */
    public void updateInstanceHealth(String instanceId, HealthStatus status) {
        try {
            String healthKey = INSTANCE_HEALTH_KEY + ":" + instanceId;
            long timestamp = System.currentTimeMillis();
            
            Map<String, Object> healthInfo = Map.of(
                "status", status.name(),
                "last_heartbeat", timestamp,
                "updated_by", "unary_heartbeat"
            );
            
            redisTemplate.opsForHash().putAll(healthKey, healthInfo);
            redisTemplate.expire(healthKey, Duration.ofMinutes(2));
            
            lastHeartbeatTimes.put(instanceId, timestamp);
            
            // Broadcast health update to all listening clients
            broadcastHealthUpdate(instanceId, status, timestamp);
            
            logger.debug("Updated and broadcasted health for instance {} to {}", instanceId, status);
            
        } catch (Exception e) {
            logger.error("Failed to update health for instance {}: {}", instanceId, e.getMessage(), e);
        }
    }

    /**
     * Update instance health information and broadcast to listening clients.
     * This method accepts a String status for compatibility with existing code.
     */
    public void updateInstanceHealth(String instanceId, String status) {
        try {
            HealthStatus healthStatus = HealthStatus.valueOf(status.toUpperCase());
            updateInstanceHealth(instanceId, healthStatus);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid health status '{}' for instance {}, defaulting to UNKNOWN", status, instanceId);
            updateInstanceHealth(instanceId, HealthStatus.UNKNOWN);
        }
    }
    
    /**
     * Broadcast a health update to all registered stream clients.
     */
    private void broadcastHealthUpdate(String instanceId, HealthStatus status, long timestamp) {
        HealthStreamResponse healthUpdate = HealthStreamResponse.newBuilder()
                .setInstanceId(instanceId)
                .setStatus(status)
                .setTimestamp(timestamp)
                .putMetadata("broadcast", "true")
                .build();
        
        // Send to all registered clients (not just the instance that sent the heartbeat)
        healthStreamClients.forEach((clientInstanceId, streamObserver) -> {
            try {
                streamObserver.onNext(healthUpdate);
                logger.debug("Broadcasted health update for {} to client {}", instanceId, clientInstanceId);
            } catch (Exception e) {
                logger.warn("Failed to broadcast health update to client {}: {}", clientInstanceId, e.getMessage());
                // Remove failed client
                healthStreamClients.remove(clientInstanceId);
            }
        });
    }
    
    /**
     * Remove a client from health stream notifications.
     */
    public void unregisterHealthStreamClient(String instanceId) {
        StreamObserver<HealthStreamResponse> removed = healthStreamClients.remove(instanceId);
        if (removed != null) {
            try {
                removed.onCompleted();
                logger.info("Unregistered health stream client for instance: {}", instanceId);
            } catch (Exception e) {
                logger.debug("Error completing stream for client {}: {}", instanceId, e.getMessage());
            }
        }
    }
    
    /**
     * Start periodic health broadcast to keep streams alive and inform about stale instances.
     */
    private void startHealthBroadcast() {
        healthBroadcastExecutor.scheduleWithFixedDelay(() -> {
            try {
                // Check for stale instances and broadcast their unhealthy status
                long staleThreshold = System.currentTimeMillis() - Duration.ofMinutes(1).toMillis();
                
                lastHeartbeatTimes.entrySet().forEach(entry -> {
                    String instanceId = entry.getKey();
                    long lastHeartbeat = entry.getValue();
                    
                    if (lastHeartbeat < staleThreshold) {
                        logger.debug("Broadcasting stale status for instance: {}", instanceId);
                        broadcastHealthUpdate(instanceId, HealthStatus.UNHEALTHY, System.currentTimeMillis());
                    }
                });
                
            } catch (Exception e) {
                logger.error("Error during health broadcast: {}", e.getMessage(), e);
            }
        }, 30, 30, TimeUnit.SECONDS); // Broadcast every 30 seconds
    }
    
    /**
     * Start background cleanup task for stale instances.
     */
    private void startCleanupTask() {
        cleanupExecutor.scheduleWithFixedDelay(() -> {
            try {
                long staleThreshold = System.currentTimeMillis() - Duration.ofMinutes(2).toMillis();
                
                lastHeartbeatTimes.entrySet().removeIf(entry -> {
                    String instanceId = entry.getKey();
                    long lastHeartbeat = entry.getValue();
                    
                    if (lastHeartbeat < staleThreshold) {
                        logger.info("Cleaning up stale instance: {}", instanceId);
                        markInstanceUnhealthy(instanceId);
                        return true;
                    }
                    return false;
                });
                
            } catch (Exception e) {
                logger.error("Error during cleanup task: {}", e.getMessage(), e);
            }
        }, 60, 60, TimeUnit.SECONDS); // Run every 60 seconds
    }
    
    /**
     * Mark an instance as unhealthy and broadcast the update.
     */
    private void markInstanceUnhealthy(String instanceId) {
        try {
            String healthKey = INSTANCE_HEALTH_KEY + ":" + instanceId;
            redisTemplate.opsForHash().put(healthKey, "status", "UNHEALTHY");
            
            broadcastHealthUpdate(instanceId, HealthStatus.UNHEALTHY, System.currentTimeMillis());
            
            logger.info("Marked instance {} as unhealthy due to stale heartbeat", instanceId);
        } catch (Exception e) {
            logger.warn("Failed to mark instance {} as unhealthy: {}", instanceId, e.getMessage());
        }
    }
    
    /**
     * Get the number of active health stream clients.
     */
    public int getActiveStreamCount() {
        return healthStreamClients.size();
    }
    
    /**
     * Check if an instance has an active health stream.
     */
    public boolean hasActiveStream(String instanceId) {
        return healthStreamClients.containsKey(instanceId);
    }
    
    /**
     * Shutdown the service and cleanup resources.
     */
    public void shutdown() {
        logger.info("Shutting down streaming heartbeat service...");
        
        // Close all active streams
        healthStreamClients.forEach((instanceId, streamObserver) -> {
            try {
                streamObserver.onCompleted();
            } catch (Exception e) {
                logger.warn("Error closing stream for instance {}: {}", instanceId, e.getMessage());
            }
        });
        
        healthStreamClients.clear();
        lastHeartbeatTimes.clear();
        cleanupExecutor.shutdown();
        healthBroadcastExecutor.shutdown();
    }
}